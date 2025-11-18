"""Shared data models for Axiell adapter steps."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field, model_validator


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
    changeset_id: str | None = None

    @model_validator(mode="after")
    def _ensure_changeset_ids(self) -> AxiellAdapterTransformerEvent:
        ids = list(self.changeset_ids)
        if not ids and self.changeset_id is not None:
            ids = [self.changeset_id]
        if not ids:
            raise ValueError("At least one changeset_id must be provided")
        self.changeset_ids = ids
        self.changeset_id = ids[0]
        return self


class WindowLoadResult(BaseModel):
    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    changeset_id: str | None = None
    last_error: str | None = None


class LoaderResponse(BaseModel):
    summaries: list[WindowLoadResult]
    changeset_ids: list[str] = Field(default_factory=list)
    changeset_id: str | None = None
    record_count: int
    job_id: str

    @model_validator(mode="after")
    def _sync_changesets(self) -> LoaderResponse:
        ids = list(self.changeset_ids)
        if self.changeset_id and self.changeset_id not in ids:
            ids.insert(0, self.changeset_id)
        if ids and not self.changeset_id:
            self.changeset_id = ids[0]
        self.changeset_ids = ids
        return self


class TransformResult(BaseModel):
    changeset_ids: list[str] = Field(default_factory=list)
    changeset_id: str
    indexed: int
    errors: list[str]
    job_id: str | None = None

    @model_validator(mode="after")
    def _sync(self) -> TransformResult:
        ids = list(self.changeset_ids)
        if self.changeset_id not in ids:
            ids.insert(0, self.changeset_id)
        self.changeset_ids = ids
        return self


__all__ = [
    "AxiellAdapterLoaderEvent",
    "WindowLoadResult",
    "LoaderResponse",
    "AxiellAdapterTransformerEvent",
    "TransformResult",
]
