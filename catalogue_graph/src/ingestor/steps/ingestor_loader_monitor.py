import typing

from clients.metric_reporter import MetricReporter
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
)
from utils.reporting import LoaderReport


def write_s3_report(event: IngestorIndexerLambdaEvent) -> LoaderReport:
    pipeline_date = event.pipeline_date
    job_id = event.job_id

    print(f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}...")

    if event.objects_to_index is None:
        raise ValueError(f"The event {event} has no objects to index.")

    sum_file_size = sum(o.content_length for o in event.objects_to_index)
    sum_record_count = sum(o.record_count for o in event.objects_to_index)

    report = LoaderReport(
        **event,
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )
    report.write()
    return report


def put_metrics(report: LoaderReport) -> None:
    dimensions = {
        "ingestor_type": report.ingestor_type,
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


def handler(event: IngestorIndexerLambdaEvent) -> None:
    report = write_s3_report(event)

    if event.report_results:
        put_metrics(report)
    else:
        print("Skipping sending report metrics.")


def lambda_handler(event: dict, context: typing.Any) -> dict:
    handler(IngestorIndexerLambdaEvent(**event))
    return event
