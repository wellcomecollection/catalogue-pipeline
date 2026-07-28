"""Pydantic models for the Axiell to Folio sync step."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class SyncSuccessEntry(BaseModel):
    """One successfully synced record in the run report."""

    source_id: str
    changeset_id: str
    instance_action: str | None
    holdings_action: str | None
    item_action: str | None
    timestamp: str


class SyncErrorEntry(BaseModel):
    """One failed record in the run report, with the stage that failed."""

    source_id: str
    changeset_id: str
    stage: str
    error: str | list[dict[str, Any]]
    timestamp: str


class SyncDeletionEntry(BaseModel):
    """One superseded-GUID suppression (authoritative delete) in the run report."""

    guid: str
    record_id: str
    changeset_id: str
    instance_action: str | None
    holdings_action: str | None
    item_action: str | None
    timestamp: str


class AxiellFolioSyncEvent(BaseModel):
    """Input to the sync step (the ``detail`` of an axiell.adapter.completed event)."""

    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    transformer_type: str | None = None
    # ``None`` means "fall back to the DRY_RUN env var" (default true).
    dry_run: bool | None = None
    # ``None`` means "fall back to the HARD_DELETE env var" (default false). When
    # true, reconciler deletions hard-delete FOLIO records instead of suppressing.
    hard_delete: bool | None = None
    # Dev/smoke-test only: cap records processed when no changeset_ids are given.
    sample_limit: int | None = None


class AxiellFolioSyncResponse(BaseModel):
    """Output of the sync step."""

    job_id: str
    dry_run: bool
    manifest_s3_path: str | None = None
    counts: dict[str, int] = Field(default_factory=dict)
    total_successful: int = 0
    total_errors: int = 0
    total_records: int = 0
    total_deletions: int = 0
