"""Generic step event models for OAI-PMH adapters.

These models define the data contracts between adapter steps (trigger, loader)
and can be used directly or extended by specific adapters.
"""

from __future__ import annotations

from datetime import datetime

from pydantic import Field

from adapters.utils.adapter_events import BaseAdapterEvent, BaseLoaderResponse
from adapters.utils.window_summary import WindowSummary
from models.events import IncrementalWindow


class OAIPMHTriggerEvent(BaseAdapterEvent):
    """Event payload for the trigger step.

    The trigger step receives this event (typically from EventBridge scheduler)
    and computes the next harvesting window based on progress state.
    """

    now: datetime | None = None
    """Timestamp to use as 'now' for window calculations.
    If None, uses current time. Useful for testing and replay."""


class OAIPMHLoaderEvent(BaseAdapterEvent):
    """Event payload for the loader step.

    The loader step receives this event from the trigger and harvests
    records from the OAI-PMH endpoint within the specified window.
    """

    window: IncrementalWindow
    """Time range to harvest records from."""

    metadata_prefix: str | None = None
    """OAI-PMH metadata prefix to request (e.g., 'oai_marcxml')."""

    set_spec: str | None = None
    """OAI-PMH set specification to filter records."""

    max_windows: int | None = None
    """Maximum number of sub-windows to process in this batch."""

    window_minutes: int | None = None
    """Duration of each sub-window in minutes."""

    allow_partial_final_window: bool | None = None
    """Whether to allow the final sub-window to be shorter than window_minutes."""


class OAIPMHLoaderResponse(BaseLoaderResponse):
    """Response from the loader step.

    Contains summaries of processed windows and identifiers for downstream steps.
    """

    summaries: list[WindowSummary]
    """Status summaries for each processed sub-window."""

    changeset_ids: list[str] = Field(default_factory=list)
    """Identifiers for changesets created during this load."""

    changed_record_count: int
    """Total number of records that changed in this batch."""

    job_id: str
    """Job identifier linking this response to the originating trigger."""
