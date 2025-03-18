#!/usr/bin/env python

import argparse
import datetime
import pprint
import typing

from pydantic import BaseModel

from config import INGESTOR_SHARD_SIZE
from ingestor_loader import IngestorLoaderLambdaEvent
from ingestor_trigger_monitor import (
    IngestorTriggerMonitorConfig,
    IngestorTriggerMonitorLambdaEvent,
)
from ingestor_trigger_monitor import (
    handler as trigger_monitor_handler,
)
from utils.aws import get_neptune_client


class IngestorTriggerLambdaEvent(BaseModel):
    job_id: str | None = None
    pipeline_date: str | None = None


class IngestorTriggerConfig(BaseModel):
    shard_size: int = INGESTOR_SHARD_SIZE
    is_local: bool = False


def extract_data(is_local: bool) -> int:
    print("Extracting record count from Neptune ...")
    client = get_neptune_client(is_local)

    open_cypher_count_query = """
    MATCH (s:Concept)
    OPTIONAL MATCH (s:Concept)-[r]->(t)
    WITH s as source, collect(r) as relationships, collect(t) as targets
    RETURN count(*) as count
    """
    print("Running query:")
    print(open_cypher_count_query)

    count_result = client.run_open_cypher_query(open_cypher_count_query)
    number_records = int(count_result[0]["count"])

    print(f"Retrieved record count: {number_records}")

    return number_records


def transform_data(
    record_count: int, shard_size: int, job_id: str | None, pipeline_date: str | None
) -> list[IngestorLoaderLambdaEvent]:
    print("Transforming record count to shard ranges ...")

    if job_id is None:
        # generate a job_id based on the current time using an iso8601 format like 20210701T1300
        job_id = datetime.datetime.now().strftime("%Y%m%dT%H%M")

    # generate shard ranges based on the record count and shard size
    shard_ranges = []

    for start_offset in range(0, record_count, shard_size):
        end_index = min(start_offset + shard_size, record_count)
        shard_ranges.append(
            IngestorLoaderLambdaEvent(
                job_id=job_id,
                start_offset=start_offset,
                end_index=end_index,
                pipeline_date=pipeline_date,
            )
        )

    print(f"Generated {len(shard_ranges)} shard ranges.")

    return shard_ranges


def handler(
    event: IngestorTriggerLambdaEvent, config: IngestorTriggerConfig
) -> IngestorTriggerMonitorLambdaEvent:
    print(f"Received event: {event} with config {config}")

    extracted_data = extract_data(config.is_local)
    transformed_data = transform_data(
        record_count=extracted_data,
        shard_size=config.shard_size,
        job_id=event.job_id,
        pipeline_date=event.pipeline_date,
    )

    print(f"Shard ranges ({len(transformed_data)}) generated successfully.")

    return IngestorTriggerMonitorLambdaEvent(
        pipeline_date=event.pipeline_date,
        events=transformed_data,
    )


def lambda_handler(event: IngestorTriggerLambdaEvent, context: typing.Any) -> dict:
    return handler(
        IngestorTriggerLambdaEvent.model_validate(event), IngestorTriggerConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--monitoring",
        action=argparse.BooleanOptionalAction,
        help="Whether to enable monitoring, will default to False.",
    )
    parser.add_argument(
        "--force-pass",
        action=argparse.BooleanOptionalAction,
        help="Whether to force pass monitoring checks, will default to False.",
    )

    args = parser.parse_args()

    event = IngestorTriggerLambdaEvent(**args.__dict__)
    config = IngestorTriggerConfig(is_local=True)

    result = handler(event, config)

    if args.monitoring:
        trigger_monitor_handler(
            result,
            IngestorTriggerMonitorConfig(
                is_local=True, force_pass=bool(args.force_pass)
            ),
        )

    pprint.pprint(result.model_dump())


if __name__ == "__main__":
    local_handler()
