import typing

from clients.metric_reporter import MetricReporter
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor_indexer import IngestorIndexerLambdaEvent
from models.step_events import IngestorMonitorStepEvent
from utils.reporting import LoaderReport, build_indexer_report
from utils.safety import validate_fractional_change


class IngestorLoaderMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorIndexerLambdaEvent]


class IngestorLoaderMonitorConfig(IngestorMonitorStepEvent):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    percentage_threshold: float = 0.1

    is_local: bool = False


def validate_events(events: list[IngestorIndexerLambdaEvent]) -> None:
    distinct_pipeline_dates = {e.pipeline_date or "dev" for e in events}
    assert len(distinct_pipeline_dates) == 1, "pipeline_date mismatch! Stopping."

    distinct_index_dates = {e.index_date or "dev" for e in events}
    assert len(distinct_index_dates) == 1, "index_date mismatch! Stopping."

    distinct_job_ids = {e.job_id for e in events}
    assert len(distinct_job_ids) == 1, "job_id mismatch! Stopping."

    # assert there are no empty content lengths
    content_lengths = [e.object_to_index.content_length for e in events]
    assert all(content_lengths), "Empty content length found! Stopping."

    # assert there are no empty record counts
    record_counts = [e.object_to_index.record_count for e in events]
    assert all(record_counts), "Empty record count found! Stopping."


def run_check(
    event: IngestorLoaderMonitorLambdaEvent, config: IngestorLoaderMonitorConfig
) -> LoaderReport:
    pipeline_date = event.events[0].pipeline_date or "dev"
    index_date = event.events[0].index_date or "dev"
    job_id = event.events[0].job_id

    force_pass = config.force_pass or event.force_pass

    print(
        f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}, force_pass: {force_pass} ..."
    )

    validate_events(event.events)

    sum_file_size = sum((e.object_to_index.content_length or 0) for e in event.events)
    sum_record_count = sum((e.object_to_index.record_count or 0) for e in event.events)

    current_report = LoaderReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id or "dev",
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )

    latest_report = LoaderReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        # load latest report by not passing job_id
        ignore_missing=True,
    )

    if latest_report is not None:
        # check if the sum file size has changed by more than the threshold,
        # we are ignoring the record count for now, as this will be the same as the trigger step
        delta = current_report.total_file_size - latest_report.total_file_size
        validate_fractional_change(
            modified_size=delta,
            total_size=latest_report.total_file_size,
            fractional_threshold=config.percentage_threshold,
            force_pass=force_pass,
        )

    # build and write the final pipeline report to s3
    build_indexer_report(current_report, latest_report)

    current_report.write()
    current_report.write(latest=True)

    return current_report


def report_results(
    report: LoaderReport,
    send_report: bool,
) -> None:
    dimensions = {
        "pipeline_date": report.pipeline_date,
        "index_date": report.index_date,
        "step": "ingestor_loader_monitor",
        "job_id": report.job_id,
    }

    print(f"Reporting results {report}, {dimensions} ...")
    if send_report:
        reporter = MetricReporter("catalogue_graph_ingestor")
        reporter.put_metric_data(
            metric_name="total_file_size",
            value=report.total_file_size,
            dimensions=dimensions,
        )
    else:
        print("Skipping sending report metrics.")


def handler(
    event: IngestorLoaderMonitorLambdaEvent, config: IngestorLoaderMonitorConfig
) -> None:
    print("Checking output of ingestor_loader ...")
    send_report = event.report_results or config.report_results

    try:
        report = run_check(event, config)
        report_results(report, send_report)
    except ValueError as e:
        print(f"Check failed: {e}")
        raise e

    print("Check complete.")


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
