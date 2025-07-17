import typing

from models.step_events import IngestorMonitorStepEvent
from utils.reporting import IndexerReport


class IngestorIndexerMonitorLambdaEvent(IngestorMonitorStepEvent):
    success_count: int


def build_indexer_report(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    pipeline_date = events[0].pipeline_date or "dev"
    index_date = events[0].index_date or "dev"
    job_id = events[0].job_id

    sum_success_count = sum((e.success_count or 0) for e in events)

    latest_report: IndexerReport | None = IndexerReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        # load the latest report without job_id
        ignore_missing=True,
    )

    current_report = IndexerReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        success_count=sum_success_count,
        previous_job_id=latest_report.job_id if latest_report else None,
    )

    current_report.write()
    current_report.write(latest=True)


def handler(events: list[IngestorIndexerMonitorLambdaEvent]) -> None:
    print("Preparing concepts pipeline reports ...")

    build_indexer_report(events)

    print("Report complete.")
    return


def lambda_handler(
    events: list[IngestorIndexerMonitorLambdaEvent], context: typing.Any
) -> dict:
    validated_events = [
        IngestorIndexerMonitorLambdaEvent.model_validate(event) for event in events
    ]

    handler(validated_events)

    return IngestorMonitorStepEvent(
        pipeline_date=events[0].pipeline_date,
        index_date=events[0].index_date,
        job_id=events[0].job_id,
    ).model_dump()
