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

import structlog
from elasticsearch import Elasticsearch

from inferrer.models import (
    DEFAULT_PARTITION_SIZE,
    FindWorkEvent,
    FindWorkResult,
    InferenceManagerEvent,
)
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
    get_images_initial_index_name,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

SCAN_BATCH_SIZE = 1000


def scan_ids(es_client: Elasticsearch, index_name: str, query: dict) -> list[str]:
    """Return the `_id`s of all documents matching `query`, using a PIT + search_after."""
    pit_id = es_client.open_point_in_time(index=index_name, keep_alive="5m")["id"]
    ids: list[str] = []
    search_after = None
    try:
        while True:
            body: dict = {
                "query": query,
                "size": SCAN_BATCH_SIZE,
                "pit": {"id": pit_id, "keep_alive": "5m"},
                "sort": [{"_shard_doc": "asc"}],
                "_source": False,
            }
            if search_after is not None:
                body["search_after"] = search_after

            result = es_client.search(body=body)
            hits = result["hits"]["hits"]
            if not hits:
                break

            ids.extend(hit["_id"] for hit in hits)
            search_after = hits[-1]["sort"]
            if result.get("pit_id"):
                pit_id = result["pit_id"]
    finally:
        es_client.close_point_in_time(body={"id": pit_id})

    return ids


def partition(ids: list[str], size: int) -> list[list[str]]:
    return [ids[i : i + size] for i in range(0, len(ids), size)]


def handler(
    event: FindWorkEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> FindWorkResult:
    setup_logging(execution_context)

    es_client = get_client("inferrer", event.pipeline_date, es_mode)
    index_name = get_images_initial_index_name(event)
    query = event.to_elasticsearch_query("modifiedTime")

    ids = scan_ids(es_client, index_name, query)
    partitions = [
        InferenceManagerEvent(
            ids=chunk,
            pipeline_date=event.pipeline_date,
            index_dates=event.index_dates,
            environment=event.environment,
        )
        for chunk in partition(ids, event.partition_size)
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
