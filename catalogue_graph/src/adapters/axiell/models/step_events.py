from datetime import datetime

from pydantic import Field

from adapters.utils.adapter_events import BaseAdapterEvent
from models.events import IncrementalWindow, WindowEvent


class AxiellAdapterEvent(BaseAdapterEvent, WindowEvent):
    """Base event for all Axiell adapter steps (windowed + job_id)."""


class AxiellAdapterTriggerEvent(AxiellAdapterEvent):
    now: datetime | None = None


class AxiellAdapterLoaderEvent(AxiellAdapterEvent):
    window: IncrementalWindow
    metadata_prefix: str | None = None
    set_spec: str | None = None
    max_windows: int | None = None
    window_minutes: int | None = None
    allow_partial_final_window: bool | None = None

    @property
    def window_start(self) -> datetime:
        return self.window.start_time

    @property
    def window_end(self) -> datetime:
        return self.window.end_time


class AxiellAdapterTransformerEvent(AxiellAdapterEvent):
    changeset_ids: list[str] = Field(default_factory=list)
