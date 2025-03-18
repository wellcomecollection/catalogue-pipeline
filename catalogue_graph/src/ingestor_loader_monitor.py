import boto3
import smart_open
from pydantic import BaseModel, typing

from clients.metric_reporter import MetricReporter
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor_indexer import IngestorIndexerLambdaEvent
from models.step_events import IngestorMonitorStepEvent


class IngestorLoaderMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorIndexerLambdaEvent]


class IngestorLoaderMonitorConfig(IngestorMonitorStepEvent):
    loader_s3_bucket: str = INGESTOR_S3_BUCKET
    loader_s3_prefix: str = INGESTOR_S3_PREFIX
    percentage_threshold: float = 0.1

    is_local: bool = False


class LoaderReport(BaseModel):
    pipeline_date: str
    job_id: str
    record_count: int
    total_file_size: int


def run_check(
    event: IngestorLoaderMonitorLambdaEvent, config: IngestorLoaderMonitorConfig
) -> LoaderReport:
    pipeline_date = event.events[0].pipeline_date or "dev"
    assert all([(e.pipeline_date or "dev") == pipeline_date for e in event.events]), (
        "pipeline_date mismatch! Stopping."
    )
    job_id = event.events[0].job_id
    assert all([e.job_id == job_id for e in event.events]), "job_id mismatch! Stopping."
    force_pass = config.force_pass or event.force_pass

    print(
        f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}, force_pass: {force_pass} ..."
    )

    # assert there are no empty content lengths
    assert all([e.object_to_index.content_length for e in event.events]), (
        "Empty content length found! Stopping."
    )
    sum_file_size = sum([(e.object_to_index.content_length or 0) for e in event.events])

    # assert there are no empty record counts
    assert all([e.object_to_index.record_count for e in event.events]), (
        "Empty record count found! Stopping."
    )
    sum_record_count = sum(
        [(e.object_to_index.record_count or 0) for e in event.events]
    )

    current_report = LoaderReport(
        pipeline_date=pipeline_date,
        job_id=job_id or "dev",
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )

    s3_report_name = "report.loader.json"
    s3_url_current_job = f"s3://{config.loader_s3_bucket}/{config.loader_s3_prefix}/{pipeline_date}/{job_id}/{s3_report_name}"
    s3_url_latest = f"s3://{config.loader_s3_bucket}/{config.loader_s3_prefix}/{pipeline_date}/{s3_report_name}"

    # open with smart_open, check for file existence
    latest_report = None
    try:
        with smart_open.open(s3_url_latest, "r") as f:
            latest_report = LoaderReport.model_validate_json(f.read())

    # if file does not exist, ignore
    except (OSError, KeyError) as e:
        print(f"No latest report found: {e}")

    if latest_report is not None:
        # check if the sum file size has changed by more than the threshold,
        # we are ignoring the record count for now, as this will be the same as the trigger step
        delta = current_report.total_file_size - latest_report.total_file_size
        percentage = abs(delta) / latest_report.total_file_size

        if percentage > config.percentage_threshold:
            error_message = f"Percentage change {percentage} exceeds threshold {config.percentage_threshold}!"
            if force_pass:
                print(f"Force pass enabled: {error_message}, but continuing.")
            else:
                raise ValueError(error_message)
        else:
            print(
                f"Percentage change {percentage} ({delta}/{latest_report.total_file_size}) is within threshold {config.percentage_threshold}."
            )

    transport_params = {"client": boto3.client("s3")}

    # write the current report to s3 as latest
    with smart_open.open(s3_url_latest, "w", transport_params=transport_params) as f:
        f.write(current_report.model_dump_json())

    # write the current report to s3 as job_id
    with smart_open.open(
        s3_url_current_job, "w", transport_params=transport_params
    ) as f:
        f.write(current_report.model_dump_json())

    return current_report


def report_results(
    report: LoaderReport,
    report_results: bool,
) -> None:
    dimensions = {
        "pipeline_date": report.pipeline_date,
        "step": "ingestor_loader_monitor",
        "job_id": report.job_id,
    }

    print(f"Reporting results {report}, {dimensions} ...")
    if report_results:
        reporter = MetricReporter("catalogue_graph_ingestor")
        reporter.put_metric_data(
            metric_name="total_file_size",
            value=report.total_file_size,
            dimensions=dimensions,
        )
    else:
        print("Skipping sending report metrics.")

    return


def handler(
    event: IngestorLoaderMonitorLambdaEvent, config: IngestorLoaderMonitorConfig
) -> None:
    print("Checking output of ingestor_loader ...")

    report = None
    try:
        report = run_check(event, config)
    except ValueError as e:
        print(f"Check failed: {e}")
        raise e

    if report is not None and event.report_results:
        report_results(report, config.report_results)

    print("Check complete.")
    return


def lambda_handler(
    event: list[IngestorIndexerLambdaEvent] | IngestorLoaderMonitorLambdaEvent,
    context: typing.Any,
) -> list[dict]:
    handler_event = None
    if isinstance(event, list):
        handler_event = IngestorLoaderMonitorLambdaEvent(events=event)
    else:
        handler_event = event

    handler(
        event=handler_event,
        config=IngestorLoaderMonitorConfig(),
    )

    return [e.model_dump() for e in handler_event.events]
