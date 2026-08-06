"""
Sync outcome models — the result of writing a record to FOLIO.

These describe *what happened* during an upsert (per-entity action + errors) and
are produced by ``upsert`` / ``run_sync``, not by the mapping. They are the
FOLIO-write side of the pipeline, distinct from the mapping payload contracts in
``mapping.payloads``.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class EntityResult(BaseModel):
    """Outcome of upserting a single FOLIO entity (instance / holdings / item)."""

    action: str | None = None
    id: str | None = None


class UpsertError(BaseModel):
    """A single error recorded during an upsert attempt."""

    type: str
    detail: str


class UpsertResult(BaseModel):
    """The complete result of :func:`upsert.upsert_from_payloads`."""

    source_id: str
    mapping_version: str
    instance: EntityResult = Field(default_factory=EntityResult)
    holdings: EntityResult = Field(default_factory=EntityResult)
    item: EntityResult = Field(default_factory=EntityResult)
    errors: list[UpsertError] = Field(default_factory=list)


class GuidCascadeResult(BaseModel):
    """Result of a reconciler cascade over one superseded GUID.

    Shared by :func:`upsert.suppress_by_guid` (soft-suppress) and
    :func:`upsert.delete_by_guid` (hard-delete); the ``action`` on each entity
    (``suppress`` / ``delete`` / ``skip``) records which was applied.
    """

    guid: str
    instance: EntityResult = Field(default_factory=EntityResult)
    holdings: EntityResult = Field(default_factory=EntityResult)
    item: EntityResult = Field(default_factory=EntityResult)
    errors: list[UpsertError] = Field(default_factory=list)
