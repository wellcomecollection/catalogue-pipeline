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
    changeset_id: str | None = None
