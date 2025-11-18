from datetime import datetime

from pydantic import BaseModel, Field


class AxiellAdapterEvent(BaseModel):
    """Base event for all Axiell adapter steps.
    job_id is a unique identifier for the overall pipeline run.
    """

    job_id: str


class AxiellAdapterTriggerEvent(AxiellAdapterEvent):
    now: datetime | None = None


class AxiellAdapterLoaderEvent(AxiellAdapterEvent):
    window_key: str = Field(
        ..., description="Unique identifier for the requested window"
    )
    window_start: datetime
    window_end: datetime
    metadata_prefix: str
    set_spec: str
    max_windows: int | None = None


class AxiellAdapterTransformerEvent(AxiellAdapterEvent):
    changeset_ids: list[str] = Field(default_factory=list)
