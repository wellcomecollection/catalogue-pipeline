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


class EbscoAdapterTriggerEvent(EbscoAdapterEvent):
    job_id: str


class EbscoAdapterLoaderEvent(EbscoAdapterEvent):
    job_id: str
    file_location: str
    # If the source file was already processed, this carries the existing changeset id
    # allowing the loader to skip work and the transformer to re-use it.
    changeset_id: str | None = None


class EbscoAdapterTransformerEvent(EbscoAdapterEvent):
    job_id: str
    # Propagate original source file location so transformer can record tracking.
    # Optional to allow full re-transform runs that aren't tied to a single source file.
    file_location: str | None = None
    changeset_id: str | None = None


