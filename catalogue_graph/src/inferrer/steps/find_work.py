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
from itertools import batched

import structlog

from inferrer.models import (
    DEFAULT_PARTITION_SIZE,
    FindWorkEvent,
    FindWorkResult,
    InferenceManagerEvent,
)
from inferrer.source import ImagesInitialSource
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


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


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="inference_find_work",
    )
    return handler(FindWorkEvent(**event), execution_context).model_dump(mode="json")


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
