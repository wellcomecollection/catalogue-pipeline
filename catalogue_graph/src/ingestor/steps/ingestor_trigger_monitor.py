import typing

from clients.metric_reporter import MetricReporter
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor.models.step_events import (
    IngestorMonitorStepEvent,
    IngestorTriggerMonitorLambdaEvent,
)
from utils.reporting import TriggerReport
from utils.safety import validate_fractional_change


class IngestorTriggerMonitorConfig(IngestorMonitorStepEvent):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    percentage_threshold: float = 0.1
    is_local: bool = False


def run_check(
    event: IngestorTriggerMonitorLambdaEvent, config: IngestorTriggerMonitorConfig
) -> TriggerReport:
    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date or "dev"
    force_pass = config.force_pass or event.force_pass

    loader_events = event.events
    # assert all job_ids are the same
    job_id = loader_events[0].job_id
    assert all([e.job_id == job_id for e in loader_events]), (
        "job_id mismatch! Stopping."
    )

    print(
        f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}, force_pass: {force_pass} ..."
    )

    # get the highest end_index
    record_count = max([e.end_index for e in loader_events])

    current_report = TriggerReport(
        record_count=record_count,
        job_id=job_id,
        pipeline_date=pipeline_date,
        index_date=index_date,
    )

    latest_report: TriggerReport | None = TriggerReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        # load the latest report without job_id
        ignore_missing=True,
    )

    if latest_report is not None:
        # check if the record_count has changed by more than the threshold
        delta = current_report.record_count - latest_report.record_count
        validate_fractional_change(
            modified_size=delta,
            total_size=latest_report.record_count,
            fractional_threshold=config.percentage_threshold,
            force_pass=force_pass,
        )

    current_report.write()
    current_report.write(latest=True)

    return current_report


def report_results(
    report: TriggerReport,
    send_report: bool,
) -> None:
    dimensions = {
        "pipeline_date": report.pipeline_date,
        "index_date": report.index_date,
        "step": "ingestor_trigger_monitor",
        "job_id": report.job_id or "unspecified",
    }

    print(f"Reporting results {report}, {dimensions} ...")
    if send_report:
        reporter = MetricReporter("catalogue_graph_ingestor")
        reporter.put_metric_data(
            metric_name="record_count", value=report.record_count, dimensions=dimensions
        )
    else:
        print("Skipping sending report metrics.")

    return


def handler(
    event: IngestorTriggerMonitorLambdaEvent, config: IngestorTriggerMonitorConfig
) -> None:
    print("Checking output of ingestor_trigger ...")
    send_report = event.report_results or config.report_results

    report = run_check(event, config)
    report_results(report, send_report)

    print("Check complete.")
    return


def lambda_handler(
    event: IngestorTriggerMonitorLambdaEvent, context: typing.Any
) -> list[dict]:
    validated_event = IngestorTriggerMonitorLambdaEvent.model_validate(event)
    handler(
        validated_event,
        IngestorTriggerMonitorConfig(),
    )

    return [e.model_dump() for e in validated_event.events]
