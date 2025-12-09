"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently.
"""

from __future__ import annotations

from adapters.utils.adapter_events import BaseAdapterEvent


class EbscoAdapterEvent(BaseAdapterEvent):
    """Base event for all EBSCO adapter steps (job-scoped)."""


class EbscoAdapterTriggerEvent(EbscoAdapterEvent):
    pass


class EbscoAdapterLoaderEvent(EbscoAdapterEvent):
    file_location: str


class EbscoAdapterTransformerEvent(EbscoAdapterEvent):
    changeset_id: str | None = None
