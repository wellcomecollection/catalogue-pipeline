"""Shared input handling and partition hand-off for find_work steps."""

from __future__ import annotations

from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from typing import Any

import structlog
from pydantic import BaseModel

from models.find_work import FindWorkEvent, PartitionRef
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


def write_partitions_to_s3(
    partitions: Sequence[BaseModel],
    counts: Sequence[int],
    event: FindWorkEvent,
) -> list[PartitionRef]:
    """Write each partition to S3 under the event's scope-keyed prefix and
    return small refs for the state machine's Map to iterate."""

    def write_one(indexed: tuple[int, BaseModel]) -> PartitionRef:
        index, partition = indexed
        s3_uri = event.partition_s3_uri(index)
        pydantic_to_s3_json(partition, s3_uri)
        return PartitionRef(s3_uri=s3_uri, count=counts[index])

    with ThreadPoolExecutor(max_workers=S3_WRITE_PARALLELISM) as pool:
        refs = list(pool.map(write_one, enumerate(partitions)))

    logger.info(
        "Wrote partitions to S3",
        partition_count=len(refs),
        s3_prefix="/".join(event.s3_prefix_parts),
    )
    return refs
