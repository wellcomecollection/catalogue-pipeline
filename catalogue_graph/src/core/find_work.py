"""Shared input handling and partition hand-off for find_work steps."""

from __future__ import annotations

from collections.abc import Callable, Iterable, Sequence
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from itertools import batched
from typing import Any

import structlog
from pydantic import BaseModel

from models.find_work import PartitionRef
from utils.aws import pydantic_to_s3_json

logger = structlog.get_logger(__name__)

# Concurrency for writing partition files to S3 (one small object per partition).
S3_WRITE_PARALLELISM = 16

# Window end lags the scheduled time so recently written documents have indexed.
SCHEDULE_INDEXING_LAG = timedelta(minutes=5)


def normalise_lambda_input(event: dict, defaults: dict[str, Any]) -> dict:
    """Normalise a find-work Lambda payload into FindWorkEvent fields.

    Scheduled runs send scheduled_time, which becomes the window end minus an
    indexing lag; replays pass ids (source_identifiers is accepted as an alias)
    or an explicit window. With no scope at all, full: true is required so a
    malformed invoke fails loudly instead of scanning the whole index.
    Deployment-identity defaults (pipeline_date etc.) fill only absent fields.
    """
    data = {k: v for k, v in event.items() if v is not None}
    scheduled_time = data.pop("scheduled_time", None)
    full = data.pop("full", None)

    if data.get("ids") is None:
        alias = data.pop("source_identifiers", None)
        if alias is not None:
            data["ids"] = alias

    if data.get("ids") is None and data.get("window") is None:
        if scheduled_time is not None:
            end_time = datetime.fromisoformat(scheduled_time) - SCHEDULE_INDEXING_LAG
            data["window"] = {"end_time": end_time}
        elif full is not True:
            raise ValueError(
                "No ids, window or scheduled_time given; "
                "pass 'full': true to scan the whole index."
            )

    for key, value in defaults.items():
        data.setdefault(key, value)
    return data


def plan_partitions[PartitionT: BaseModel](
    ids: Iterable[str],
    partition_size: int,
    build_partition: Callable[[list[str], int], PartitionT],
) -> list[PartitionT]:
    """Slice ids into worker events of at most partition_size ids each."""
    return [
        build_partition(list(chunk), index)
        for index, chunk in enumerate(batched(ids, partition_size))
    ]


def group_by_weight(
    weighted_ids: Iterable[tuple[str, int]], max_weight: int
) -> list[list[str]]:
    """Pack ids, in order, into groups weighing at most max_weight; an id heavier
    than max_weight gets a group of its own."""
    groups: list[list[str]] = []
    current: list[str] = []
    current_weight = 0
    for item_id, weight in weighted_ids:
        if current and current_weight + weight > max_weight:
            groups.append(current)
            current, current_weight = [], 0
        current.append(item_id)
        current_weight += weight
    if current:
        groups.append(current)
    return groups


def write_partitions_to_s3(
    partitions: Sequence[BaseModel],
    counts: Sequence[int],
    partition_s3_uri: Callable[[int], str],
) -> list[PartitionRef]:
    """Write each partition to the S3 URI the caller derives from its index and
    return small refs for the state machine's Map to iterate."""

    def write_one(indexed: tuple[int, BaseModel]) -> PartitionRef:
        index, partition = indexed
        s3_uri = partition_s3_uri(index)
        pydantic_to_s3_json(partition, s3_uri)
        return PartitionRef(s3_uri=s3_uri, count=counts[index])

    with ThreadPoolExecutor(max_workers=S3_WRITE_PARALLELISM) as pool:
        refs = list(pool.map(write_one, enumerate(partitions)))

    logger.info(
        "Wrote partitions to S3",
        partition_count=len(refs),
        s3_prefix=partition_s3_uri(0).rsplit("/", 1)[0],
    )
    return refs
