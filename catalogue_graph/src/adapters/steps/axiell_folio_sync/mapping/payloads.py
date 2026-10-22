"""
FOLIO payload contracts — the typed shape we send to the Inventory API.

These Pydantic models are the FOLIO *contract*: a malformed payload (missing or
ill-typed required field, typo'd key) fails at build time — before any OKAPI
call — instead of surfacing as a FOLIO 422. The builders in ``builders.py``
construct them; ``upsert`` serializes them.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class IdRef(BaseModel):
    """A FOLIO {"id": "<uuid>"} reference object."""

    id: str


class Status(BaseModel):
    name: str = "Available"


class Note(BaseModel):
    # A typed FOLIO item note. The builders set itemNoteTypeId directly. A note
    # carrying only a noteType name has it resolved later by
    # upsert.entities._resolve_item_note_types.
    model_config = ConfigDict(extra="allow")
    note: str
    itemNoteTypeId: str | None = None
    noteType: str | None = None
    staffOnly: bool = False


class Identifier(BaseModel):
    """A FOLIO instance identifier — a typed local/system number."""

    identifierTypeId: str
    value: str


class Instance(BaseModel):
    model_config = ConfigDict(extra="forbid")  # guard against typo'd keys in our code
    hrid: str
    title: str
    source: str = "FOLIO"
    instanceTypeId: str
    identifiers: list[Identifier] | None = None


class Holdings(BaseModel):
    model_config = ConfigDict(extra="forbid")
    hrid: str
    instanceId: str | None = None  # injected by the upsert orchestrator
    sourceId: str
    permanentLocationId: str


class Item(BaseModel):
    model_config = ConfigDict(extra="forbid")
    hrid: str
    holdingsRecordId: str | None = None  # injected by the upsert orchestrator
    status: Status = Field(default_factory=Status)
    materialType: IdRef
    permanentLoanType: IdRef
    permanentLocation: IdRef
    # Typed notes: the Axiell Current Location and Axiell Normal Location notes.
    # The upsert replaces notes of those types and keeps every other note.
    notes: list[Note] | None = None
    # Plain untyped strings; no tenant note type needed.
    administrativeNotes: list[str] | None = None


class PayloadMeta(BaseModel):
    """Metadata attached to a mapped payload set."""

    source_id: str
    instance_hrid: str
    holdings_hrid: str
    item_hrid: str
    mapping_version: str
    deleted: bool = False


class MappedPayloads(BaseModel):
    """The complete output of :func:`builders.select_and_build`."""

    instance: Instance
    holdings: Holdings
    item: Item
    meta: PayloadMeta
