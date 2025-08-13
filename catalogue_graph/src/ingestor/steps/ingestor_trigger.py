#!/usr/bin/env python

import argparse
import datetime
import pprint
import typing

from pydantic import BaseModel

from config import INGESTOR_SHARD_SIZE
from ingestor.models.step_events import (
    IngestorLoaderLambdaEvent,
    IngestorTriggerLambdaEvent,
    IngestorTriggerMonitorLambdaEvent,
)
from ingestor.steps.ingestor_trigger_monitor import IngestorTriggerMonitorConfig
from ingestor.steps.ingestor_trigger_monitor import handler as trigger_monitor_handler
from utils.aws import get_neptune_client
from utils.types import IngestorType


class IngestorTriggerConfig(BaseModel):
    is_local: bool = False
    shard_size: int = INGESTOR_SHARD_SIZE


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.datetime.now().strftime("%Y%m%dT%H%M")


def extract_data(ingestor_type: IngestorType, is_local: bool) -> int:
    print("Extracting record count from Neptune ...")
    client = get_neptune_client(is_local)

    if ingestor_type == "concepts":
        count_query = "MATCH (c:Concept) RETURN count(*) as count"
    elif ingestor_type == "works":
        count_query = "MATCH (w:Work) RETURN count(*) as count"
    else:
        raise ValueError(f"Unknown ingestor type: {ingestor_type}.")

    count_result = client.run_open_cypher_query(count_query)
    number_records = int(count_result[0]["count"])

    print(f"Retrieved record count: {number_records}")

    return number_records


def transform_data(
    record_count: int, event: IngestorTriggerLambdaEvent, config: IngestorTriggerConfig
) -> list[IngestorLoaderLambdaEvent]:
    print("Transforming record count to shard ranges ...")

    # generate shard ranges based on the record count and shard size
    shard_ranges = []

    for start_offset in range(0, record_count, config.shard_size):
        end_index = min(start_offset + config.shard_size, record_count)
        shard_ranges.append(
            IngestorLoaderLambdaEvent(
                **event.model_dump(),
                start_offset=start_offset,
                end_index=end_index,
            )
        )

    return shard_ranges


def handler(
    event: IngestorTriggerLambdaEvent, config: IngestorTriggerConfig
) -> IngestorTriggerMonitorLambdaEvent:
    print(f"Received event: {event} with config {config}")

    extracted_data = extract_data(event.ingestor_type, config.is_local)
    transformed_data = transform_data(
        record_count=extracted_data, event=event, config=config
    )

    print(f"Shard ranges ({len(transformed_data)}) generated successfully.")

    return IngestorTriggerMonitorLambdaEvent(
        **event.model_dump(),
        events=transformed_data,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(
        IngestorTriggerLambdaEvent(**event), IngestorTriggerConfig()
    ).model_dump()


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
    config = IngestorTriggerConfig(is_local=True)

    result = handler(event, config)

    if args.monitoring:
        trigger_monitor_handler(result, IngestorTriggerMonitorConfig())

    pprint.pprint(result.model_dump())


if __name__ == "__main__":
    local_handler()
