import typing

from ingestor.models.step_events import (
    IngestorIndexerMonitorLambdaEvent,
    IngestorMonitorStepEvent,
)
from utils.reporting import IndexerReport


def write_s3_report(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    pipeline_date = events[0].pipeline_date
    index_date = events[0].index_date
    ingestor_type = events[0].ingestor_type
    job_id = events[0].job_id

    sum_success_count = sum((e.success_count or 0) for e in events)

    report = IndexerReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ingestor_type=ingestor_type,
        job_id=job_id,
        success_count=sum_success_count,
    )
    report.write()


def handler(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    print("Preparing concepts pipeline reports ...")
    write_s3_report(events)


def lambda_handler(events: list[dict], context: typing.Any) -> dict:
    validated_events = [IngestorIndexerMonitorLambdaEvent(**e) for e in events]
    handler(validated_events)

    return IngestorMonitorStepEvent(**events[0]).model_dump()
