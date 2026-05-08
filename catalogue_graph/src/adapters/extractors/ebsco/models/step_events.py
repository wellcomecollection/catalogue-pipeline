"""Shared event models passed between EBSCO adapter pipeline steps.

This centralises the pydantic definitions so we can evolve them safely &
consistently.
"""

from __future__ import annotations

from adapters.utils.adapter_events import BaseAdapterEvent, BaseLoaderResponse


class EbscoAdapterTriggerEvent(BaseAdapterEvent):
    adapter_type: str = "ebsco"


class EbscoAdapterLoaderEvent(BaseAdapterEvent):
    adapter_type: str = "ebsco"
    file_location: str


class LoaderResponse(BaseLoaderResponse):
    pass
