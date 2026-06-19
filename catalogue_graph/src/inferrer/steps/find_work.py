#!/usr/bin/env python
"""Work-discovery step for the image-inferrer state machine.

Runs as a Lambda invoked at the start of each scheduled state-machine execution.
Given a time window (or ids/full scope), it queries the `images-initial` index
for the ids of images modified within the window and partitions them into chunks,
one per downstream inference task (fanned out by the state machine's Map state).
"""

from __future__ import annotations

import typing
from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor
from itertools import batched

import structlog

import config
from inferrer.models import (
    DEFAULT_PARTITION_SIZE,
    FindWorkEvent,
    FindWorkRefsResult,
    FindWorkResult,
    InferenceManagerEvent,
    PartitionRef,
)
from inferrer.source import ImagesInitialSource
from utils.argparse import add_pipeline_event_args
from utils.aws import pydantic_to_s3_json
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

# Concurrency for writing partition files to S3 (one small object per partition).
S3_WRITE_PARALLELISM = 16


def handler(
    event: FindWorkEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> FindWorkResult:
    setup_logging(execution_context)

    es_client = get_client("inferrer", event.pipeline_date, es_mode)

    ids = list(ImagesInitialSource(event, es_client).stream_raw())
    partitions = [
        InferenceManagerEvent(
            ids=list(chunk),
            pipeline_date=event.pipeline_date,
            index_dates=event.index_dates,
            environment=event.environment,
        )
        for chunk in batched(ids, event.partition_size)
    ]

    logger.info(
        "Found work",
        mode=event.mode_label,
        image_count=len(ids),
        partition_count=len(partitions),
        partition_size=event.partition_size,
    )
    return FindWorkResult(partitions=partitions)


def _partition_s3_uri(event: FindWorkEvent, run_id: str, index: int) -> str:
    bucket = config.CATALOGUE_GRAPH_S3_BUCKETS[event.environment]
    return (
        f"s3://{bucket}/{config.INFERRER_S3_PREFIX}/{event.pipeline_date}"
        f"/find_work/{run_id}/partition-{index}.json"
    )


def write_partitions_to_s3(
    partitions: list[InferenceManagerEvent], event: FindWorkEvent, run_id: str
) -> list[PartitionRef]:
    """Write each partition to S3 and return small refs.

    The state machine's Map then iterates these refs (a few hundred bytes each)
    rather than the full partitions, keeping the find-work result well under the
    Step Functions 256 KB state limit. Each inference task resolves its ref back
    to the full `InferenceManagerEvent` from S3.
    """

    def write_one(indexed: tuple[int, InferenceManagerEvent]) -> PartitionRef:
        index, partition = indexed
        s3_uri = _partition_s3_uri(event, run_id, index)
        pydantic_to_s3_json(partition, s3_uri)
        return PartitionRef(s3_uri=s3_uri, image_count=len(partition.ids or []))

    with ThreadPoolExecutor(max_workers=S3_WRITE_PARALLELISM) as pool:
        refs = list(pool.map(write_one, enumerate(partitions)))

    logger.info("Wrote partitions to S3", partition_count=len(refs), run_id=run_id)
    return refs


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    parsed_event = FindWorkEvent(**event)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="inference_find_work",
    )
    result = handler(parsed_event, execution_context)

    # Hand the partitions off via S3 (pass-by-reference) so the Map's inline
    # payload stays small regardless of how many images the window matched.
    run_id = getattr(context, "aws_request_id", None) or get_trace_id(context)
    refs = write_partitions_to_s3(result.partitions, parsed_event, run_id)
    return FindWorkRefsResult(partitions=refs).model_dump(mode="json")


def local_handler(parser: ArgumentParser) -> None:
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "index_date_initial",
            "index_date_augmented",
            "window",
            "ids",
            "environment",
            "es_mode",
        },
    )
    parser.add_argument(
        "--partition-size",
        type=int,
        default=DEFAULT_PARTITION_SIZE,
        help="Number of image ids per downstream inference task.",
    )
    args = parser.parse_args()
    event = FindWorkEvent.from_argparser(args)
    result = handler(event, es_mode=args.es_mode)
    print(result.model_dump_json())


if __name__ == "__main__":
    main_parser: ArgumentParser = ArgumentParser()
    local_handler(main_parser)
