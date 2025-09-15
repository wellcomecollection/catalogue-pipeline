"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently (e.g. adding common attributes such as index_date).
"""

from __future__ import annotations

from pydantic import BaseModel


class EbscoAdapterEvent(BaseModel):
    """Base event for all EBSCO adapter steps.

    index_date: Allows a prior step to override the index name date component
    used by downstream indexing (falls back to pipeline date when omitted).
    """

    index_date: str | None = None
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

    Instead of embedding potentially large batch id lists directly in the
    state machine event, we persist them to S3 and return the location.

    batch_file_location: S3 URI (s3://bucket/prefix/<job_id>.ndjson) containing
    NDJSON (one JSON object per line) where each line is {"ids": [ ... ]} for a
    processed RecordBatch. ``None`` when no work was performed (e.g. idempotent
    skip).

    batch_file_bucket / batch_file_key: Explicit S3 components provided to make
    it easy for Step Functions Distributed Map ItemReader to reference the
    manifest without parsing the URI. Optional for backward compatibility.
    success_count / failure_count: indexing outcome totals.
    """

    batch_file_location: str | None = None
    batch_file_bucket: str | None = None
    batch_file_key: str | None = None
    success_count: int = 0
    failure_count: int = 0
