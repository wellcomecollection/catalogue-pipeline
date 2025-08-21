import typing

from pydantic import BaseModel

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderMonitorLambdaEvent,
)
from utils.reporting import LoaderReport
from utils.safety import validate_fractional_change


class IngestorLoaderMonitorConfig(BaseModel):
    percentage_threshold: float = 0.1
    is_local: bool = False


def validate_events(events: list[IngestorIndexerLambdaEvent]) -> None:
    distinct_pipeline_dates = {e.pipeline_date for e in events}
    assert len(distinct_pipeline_dates) == 1, "pipeline_date mismatch! Stopping."

    distinct_index_dates = {e.index_date for e in events}
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
    pipeline_date = event.pipeline_date
    index_date = event.index_date
    ingestor_type = event.ingestor_type
    job_id = event.job_id

    print(
        f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}, force_pass: {event.force_pass} ..."
    )

    validate_events(event.events)

    sum_file_size = sum((e.object_to_index.content_length or 0) for e in event.events)
    sum_record_count = sum((e.object_to_index.record_count or 0) for e in event.events)

    current_report = LoaderReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ingestor_type=ingestor_type,
        job_id=job_id,
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )

    latest_report: LoaderReport | None = LoaderReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ingestor_type=ingestor_type,
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
            force_pass=event.force_pass,
        )

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
        "job_id": report.job_id or "unspecified",
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

    report = run_check(event, config)
    report_results(report, event.report_results)

    print("Check complete.")


def lambda_handler(event: list[dict] | dict, context: typing.Any) -> list[dict]:
    # When running in production, 'event' stores a list of IngestorIndexerLambdaEvent items.
    # When running locally, it stores an IngestorLoaderMonitorLambdaEvent object.
    if isinstance(event, list):
        validated_events = [IngestorIndexerLambdaEvent(**e) for e in event]
        handler_event = IngestorLoaderMonitorLambdaEvent(
            **event[0], events=validated_events
        )
    else:
        handler_event = IngestorLoaderMonitorLambdaEvent(**event)

    handler(handler_event, IngestorLoaderMonitorConfig())
    return [e.model_dump() for e in handler_event.events]
