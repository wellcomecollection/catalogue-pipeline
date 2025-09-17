#!/usr/bin/env python

import argparse
import datetime
import pprint
import typing

from ingestor.extractors.works_extractor import GraphWorksExtractor
from ingestor.models.step_events import (
    IngestorLoaderLambdaEvent,
    IngestorTriggerLambdaEvent,
    IngestorTriggerMonitorLambdaEvent,
)
from ingestor.steps.ingestor_trigger_monitor import IngestorTriggerMonitorConfig
from ingestor.steps.ingestor_trigger_monitor import handler as trigger_monitor_handler
from utils.aws import get_neptune_client
from utils.types import IngestorType


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.datetime.now().strftime("%Y%m%dT%H%M")


def extract_data(ingestor_type: IngestorType, is_local: bool) -> int:
    # Can't enforce fix slice size when using ES pit!
    if ingestor_type == "concepts":
        print("Extracting record count from Neptune ...")
        client = get_neptune_client(is_local)
        count_query = "MATCH (c:Concept) RETURN count(*) as count"
        count_result = client.run_open_cypher_query(count_query)
        record_count = int(count_result[0]["count"])
        pit_id = None
    elif ingestor_type == "works":
        source = GraphWorksExtractor("2025-08-14", 0, 10000, is_local)
        pit_id, record_count = source.test(None)
    else:
        raise ValueError(f"Unknown ingestor type: {ingestor_type}.")

    print(f"Retrieved record count: {record_count}")

    return pit_id, record_count


def handler(
    event: IngestorTriggerLambdaEvent, is_local: bool = False
) -> IngestorTriggerMonitorLambdaEvent:
    pit_id, record_count = extract_data(event.ingestor_type, is_local)

    # In incremental mode, we only want one worker to prevent processing related works multiple times!
    events = []
    for i in range(5):
        events.append(
            IngestorLoaderLambdaEvent(
                **event.model_dump(),
                pit_id=pit_id,
                slice_index=i,
            )
        )

    print(f"Shard ranges ({len(events)}) generated successfully.")

    return IngestorTriggerMonitorLambdaEvent(
        **event.model_dump(),
        events=events,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(IngestorTriggerLambdaEvent(**event)).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run (works or concepts).",
        required=True,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
        default=create_job_id(),
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The concepts index date that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--monitoring",
        action=argparse.BooleanOptionalAction,
        help="Whether to enable monitoring, will default to False.",
        default=False,
    )
    parser.add_argument(
        "--force-pass",
        action=argparse.BooleanOptionalAction,
        help="Whether to force pass monitoring checks, will default to False.",
        default=False,
    )

    args = parser.parse_args()
    event = IngestorTriggerLambdaEvent(**args.__dict__)

    result = handler(event, is_local=True)

    if args.monitoring:
        trigger_monitor_handler(result, IngestorTriggerMonitorConfig())

    pprint.pprint(result.model_dump())


if __name__ == "__main__":
    local_handler()
