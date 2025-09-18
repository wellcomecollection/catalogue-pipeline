import typing

from ingestor.models.step_events import (
    IngestorIndexerMonitorLambdaEvent,
    IngestorMonitorStepEvent,
)
from utils.reporting import IndexerReport


def build_indexer_report(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    pipeline_date = events[0].pipeline_date
    index_date = events[0].index_date
    ingestor_type = events[0].ingestor_type
    job_id = events[0].job_id

    # load the latest report without job_id
    latest_report: IndexerReport | None = IndexerReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ingestor_type=ingestor_type,
        ignore_missing=True,
    )

    sum_success_count = sum((e.success_count or 0) for e in events)

    current_report = IndexerReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ingestor_type=ingestor_type,
        job_id=job_id,
        success_count=sum_success_count,
        previous_job_id=latest_report.job_id if latest_report else None,
    )

    current_report.write()


def handler(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    print("Preparing concepts pipeline reports ...")
    build_indexer_report(events)


def lambda_handler(events: list[dict], context: typing.Any) -> dict:
    validated_events = [IngestorIndexerMonitorLambdaEvent(**e) for e in events]
    handler(validated_events)

    return IngestorMonitorStepEvent(**events[0]).model_dump()
