"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently.
"""

from __future__ import annotations

from adapters.utils.adapter_events import BaseAdapterEvent


class EbscoAdapterTriggerEvent(BaseAdapterEvent):
    pass


class EbscoAdapterLoaderEvent(BaseAdapterEvent):
    file_location: str
