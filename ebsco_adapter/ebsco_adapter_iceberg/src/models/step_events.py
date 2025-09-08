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
    is_processed: bool


class EbscoAdapterTransformerEvent(EbscoAdapterEvent):
    changeset_id: str | None = None
    job_id: str
