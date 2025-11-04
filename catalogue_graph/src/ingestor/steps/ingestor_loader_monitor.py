import typing

from utils.reporting import LoaderReport

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
)


def write_s3_report(event: IngestorIndexerLambdaEvent) -> LoaderReport:
    pipeline_date = event.pipeline_date
    job_id = event.job_id

    print(f"Checking loader events for pipeline_date: {pipeline_date}:{job_id}...")

    if event.objects_to_index is None:
        raise ValueError(f"The event {event} has no objects to index.")

    sum_file_size = sum(o.content_length for o in event.objects_to_index)
    sum_record_count = sum(o.record_count for o in event.objects_to_index)

    report = LoaderReport(
        **event.model_dump(),
        record_count=sum_record_count,
        total_file_size=sum_file_size,
    )
    report.write()


def handler(event: IngestorIndexerLambdaEvent) -> None:
    write_s3_report(event)


def lambda_handler(event: dict, context: typing.Any) -> dict:
    handler(IngestorIndexerLambdaEvent(**event))
    return event
