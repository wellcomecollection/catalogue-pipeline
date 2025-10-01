import typing

from ingestor.models.step_events import (
    IngestorIndexerMonitorLambdaEvent,
)
from utils.reporting import IndexerReport


def handler(event: IngestorIndexerMonitorLambdaEvent) -> None:
    print("Preparing concepts pipeline reports ...")
    report = IndexerReport(**event.model_dump())
    report.write()


def lambda_handler(event: dict, context: typing.Any) -> dict:
    handler(IngestorIndexerMonitorLambdaEvent(**event))
    return event
