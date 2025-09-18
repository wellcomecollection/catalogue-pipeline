"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently.
"""

from __future__ import annotations

from pydantic import BaseModel


class EbscoAdapterEvent(BaseModel):
    """Base event for all EBSCO adapter steps.
    job_id is a unique identifier for the overall pipeline run.
    """

    job_id: str


class EbscoAdapterTriggerEvent(EbscoAdapterEvent):
    pass


class EbscoAdapterLoaderEvent(EbscoAdapterEvent):
    file_location: str


class EbscoAdapterTransformerEvent(EbscoAdapterEvent):
    # Propagate original source file location so transformer can record tracking.
    # Optional to allow full re-transform runs that aren't tied to a single source file.
    file_location: str | None = None
    changeset_id: str | None = None


class EbscoAdapterTransformerResult(EbscoAdapterEvent):
    """Result of transformer execution passed to the next step.

    Large lists of batch ids are written to S3 (one JSON line per batch).
    """

    batch_file_bucket: str | None = None
    batch_file_key: str | None = None
    success_count: int = 0
    failure_count: int = 0
