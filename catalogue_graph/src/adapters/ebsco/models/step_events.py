"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from adapters.utils.adapter_events import BaseAdapterEvent


class EbscoAdapterTriggerEvent(BaseAdapterEvent):
    pass


class EbscoAdapterLoaderEvent(BaseAdapterEvent):
    file_location: str


class LoaderResponse(BaseModel):
    changeset_ids: list[str] = Field(default_factory=list)
    changed_record_count: int
    job_id: str


class EbscoAdapterTransformerEvent(BaseAdapterEvent):
    changeset_id: str | None = None
