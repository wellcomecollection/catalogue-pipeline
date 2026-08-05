"""Shared models for find_work work-discovery steps.

A find_work step scans an index for the ids in scope, partitions them, writes
each partition to S3 and returns small refs, so the state machine's Map payload
stays under the Step Functions 256 KB state limit regardless of window density.
"""

from __future__ import annotations

from pathlib import PurePosixPath

from pydantic import BaseModel

import config
from models.events import BasePipelineEvent


class FindWorkEvent(BasePipelineEvent):
    """Input for a work-discovery step: a time window (or ids/full)."""

    partition_size: int

    def partition_s3_uri(self, index: int) -> str:
        bucket = config.CATALOGUE_GRAPH_S3_BUCKET
        return f"s3://{bucket}/{PurePosixPath(*self.s3_prefix_parts)}/partition-{index}.json"


class PartitionRef(BaseModel):
    """A pointer to a partition event stored in S3; workers resolve it back."""

    s3_uri: str
    count: int


class FindWorkRefsResult(BaseModel):
    partitions: list[PartitionRef]
