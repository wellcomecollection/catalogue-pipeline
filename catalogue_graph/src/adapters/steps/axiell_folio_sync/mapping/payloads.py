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
    # noteType is resolved to itemNoteTypeId later by upsert._resolve_item_note_types.
    model_config = ConfigDict(extra="allow")
    note: str
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
    barcode: str | None = None
    notes: list[Note] | None = None


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
