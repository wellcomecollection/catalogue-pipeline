from datetime import datetime

from models.events import IncrementalWindow
from pydantic import BaseModel

from adapters.utils.adapter_events import BaseAdapterEvent
from adapters.utils.window_summary import WindowSummary


class AxiellAdapterTriggerEvent(BaseAdapterEvent):
    now: datetime | None = None


class AxiellAdapterLoaderEvent(BaseAdapterEvent):
    window: IncrementalWindow
    metadata_prefix: str | None = None
    set_spec: str | None = None
    max_windows: int | None = None
    window_minutes: int | None = None
    allow_partial_final_window: bool | None = None


class LoaderResponse(BaseModel):
    summaries: list[WindowSummary]
    changeset_ids: list[str] = Field(default_factory=list)
    changed_record_count: int
    job_id: str
