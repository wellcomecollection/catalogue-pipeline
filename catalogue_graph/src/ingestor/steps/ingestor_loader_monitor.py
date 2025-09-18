import typing

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderMonitorLambdaEvent,
)
from utils.reporting import LoaderReport


def write_s3_report(event: IngestorLoaderMonitorLambdaEvent) -> LoaderReport:
    pipeline_date = event.pipeline_date
    job_id = event.job_id

    print(f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}...")

    sum_file_size = sum((e.object_to_index.content_length or 0) for e in event.events)
    sum_record_count = sum((e.object_to_index.record_count or 0) for e in event.events)

    report = LoaderReport(
        **event,
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )
    report.write()
    return report


def put_metrics(report: LoaderReport) -> None:
    dimensions = {
        "pipeline_date": report.pipeline_date,
        "index_date": report.index_date,
        "step": "ingestor_loader_monitor",
        "job_id": report.job_id or "unspecified",
    }

    print(f"Reporting results {report}, {dimensions} ...")
    reporter = MetricReporter("catalogue_graph_ingestor")
    reporter.put_metric_data(
        metric_name="total_file_size",
        value=report.total_file_size,
        dimensions=dimensions,
    )


def handler(event: IngestorLoaderMonitorLambdaEvent) -> None:
    report = write_s3_report(event)

    if event.report_results:
        put_metrics(report)
    else:
        print("Skipping sending report metrics.")


def lambda_handler(event: dict, context: typing.Any) -> list[dict]:
    # When running in production, 'event' stores a list of IngestorIndexerLambdaEvent items.
    # When running locally, it stores an IngestorLoaderMonitorLambdaEvent object.
    if isinstance(event, list):
        validated_events = [IngestorIndexerLambdaEvent(**e) for e in event]
        handler_event = IngestorLoaderMonitorLambdaEvent(
            **event[0], events=validated_events
        )
    else:
        handler_event = IngestorLoaderMonitorLambdaEvent(**event)

    handler(handler_event)
    return [e.model_dump() for e in handler_event.events]
