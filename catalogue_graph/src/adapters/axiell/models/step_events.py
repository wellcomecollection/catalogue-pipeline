from datetime import datetime

from pydantic import Field

from adapters.utils.adapter_events import BaseAdapterEvent, BaseLoaderResponse
from adapters.utils.window_summary import WindowSummary
from models.events import IncrementalWindow


class AxiellAdapterTriggerEvent(BaseAdapterEvent):
    now: datetime | None = None


class AxiellAdapterLoaderEvent(BaseAdapterEvent):
    window: IncrementalWindow
    metadata_prefix: str | None = None
    set_spec: str | None = None
    max_windows: int | None = None
    window_minutes: int | None = None
    allow_partial_final_window: bool | None = None


class LoaderResponse(BaseLoaderResponse):
    summaries: list[WindowSummary]


class AxiellAdapterTransformerEvent(BaseAdapterEvent):
    changeset_ids: list[str] = Field(default_factory=list)
