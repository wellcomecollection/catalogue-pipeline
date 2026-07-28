"""
AxC → FOLIO mapping: payload models, normalization rules, and builders.

This is the single home for everything that decides *what an Axiell record
becomes in FOLIO*. The payloads are typed Pydantic models, so a malformed payload
(missing/ill-typed required field, typo'd key) fails at
build time — before any OKAPI call — instead of surfacing as a FOLIO 422.

Everything a reviewer needs to understand or change the mapping lives here:

  • MATERIAL_TYPE          AxC source code → FOLIO standard material-type name
  • DEFAULTS / hrid scheme module-level constants
  • Instance/Holdings/Item Pydantic payload contracts
  • build_instance/holdings/item   the field-by-field mapping logic
  • build_payloads          orchestration → the dict shape upsert consumes

MARC *extraction* (which subfield holds what) stays in mapper.parse_marcxml,
which yields the CanonicalRecord these builders read from.

``build_payloads`` returns
``{"instance": {...}, "holdings": {...}, "item": {...}, "meta": {...}}`` ready
for :func:`adapters.steps.axiell_folio_sync.upsert.upsert_from_payloads`.

RFC 090 references below point at the design spec:
https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync
"""

from __future__ import annotations

from collections.abc import Callable

from pydantic import BaseModel, ConfigDict, Field

from .mapper import CanonicalRecord, MappingError, extract, parse_xml
from .ref_cache import RefCache

# Bumped whenever the rules below change; stamped into every payload's meta.
VERSION = "2.1.0"


# ── inbound: which MARC field feeds which CanonicalRecord attribute ─────────────
#
# This table is the single source of truth for the Axiell/MARC *source* side.
# Spec syntax: "TAG$subfield" for datafields, "TAG" for controlfields.
MARC_SOURCE: dict[str, str] = {
    "source_id": "001",  # Axiell GUID — identifies the record
    "title": "245$a",
    "location_code": "852$b",
    "call_number": "852$h",
    "call_number_prefix": "852$c",
    "shelving_order": "852$j",
    "barcode": "949$a",
    "material_type_code": "949$c",
    "loan_type_code": "949$l",
    "copy_number": "876$p",
    "volume": "876$t",
    "electronic_access_uri": "856$u",
}


# ── record selection (RFC 090 §Record selection) ─────────────────────────────
# A record is synced to FOLIO only if it is flagged for harvest AND is item-level.
# Both are read from the harvested MARCXML.
HARVEST_FLAG_SPEC = "980$a"  # present (non-empty) = opted in for FOLIO sync
RECORD_TYPE_SPEC = "351$c"  # Axiell record type / level
RECORD_TYPE_ITEM = "ITEM"  # only item-level records are synced


def is_selected_for_sync(xml_content: str) -> bool:
    """Whether a record should be synced to FOLIO.

    True only when it carries the harvest flag (MARC ``980 $a`` present) and is of
    record type ITEM (MARC ``351 $c`` == "ITEM", case-insensitive). Records that
    fail either check are skipped entirely — never created, updated or suppressed.
    """
    root = parse_xml(xml_content)
    if not extract(root, HARVEST_FLAG_SPEC):
        return False
    record_type = (extract(root, RECORD_TYPE_SPEC) or "").strip().upper()
    return record_type == RECORD_TYPE_ITEM


def parse_marcxml(xml_content: str, *, deleted: bool = False) -> CanonicalRecord:
    """Parse a single MARCXML string into a :class:`CanonicalRecord` via MARC_SOURCE.

    Raises :class:`MappingError` if MARC 001 is absent (record cannot be identified).
    """
    root = parse_xml(xml_content)
    values = {field: extract(root, spec) for field, spec in MARC_SOURCE.items()}

    # Pop the guaranteed-present source_id so it is passed explicitly (narrowed to
    # str after the guard); the remaining values are all the optional str | None fields.
    source_id = values.pop("source_id")
    if not source_id:
        raise MappingError("Missing MARC 001 — cannot identify record")

    instance_hrid = _instance_hrid(source_id)
    holdings_hrid = _holdings_hrid(source_id)
    return CanonicalRecord(
        source_id=source_id,
        instance_hrid=instance_hrid,
        holdings_hrid=holdings_hrid,
        deleted=deleted,
        **values,
    )


def select_and_build(
    xml_content: str,
    ref_cache: RefCache,
    *,
    deleted: bool = False,
) -> MappedPayloads | None:
    """Select and build in one pass — parses XML only once.

    Returns ``None`` if the record is not selected for sync; otherwise returns
    the :class:`MappedPayloads`. Raises on malformed XML or mapping errors.
    """
    root = parse_xml(xml_content)

    # Selection check (same logic as is_selected_for_sync)
    if not extract(root, HARVEST_FLAG_SPEC):
        return None
    record_type = (extract(root, RECORD_TYPE_SPEC) or "").strip().upper()
    if record_type != RECORD_TYPE_ITEM:
        return None

    # Extract canonical record from the already-parsed XML
    values = {field: extract(root, spec) for field, spec in MARC_SOURCE.items()}
    source_id = values.pop("source_id")
    if not source_id:
        raise MappingError("Missing MARC 001 — cannot identify record")

    rec = CanonicalRecord(
        source_id=source_id,
        instance_hrid=_instance_hrid(source_id),
        holdings_hrid=_holdings_hrid(source_id),
        deleted=deleted,
        **values,
    )

    instance = build_instance(rec, ref_cache)
    holdings = build_holdings(rec, ref_cache)
    item = build_item(rec, ref_cache)

    return MappedPayloads(
        instance=instance,
        holdings=holdings,
        item=item,
        meta=PayloadMeta(
            source_id=rec.source_id,
            instance_hrid=_instance_hrid(rec.source_id),
            holdings_hrid=_holdings_hrid(rec.source_id),
            item_hrid=_item_hrid(rec.source_id),
            mapping_version=VERSION,
            deleted=deleted,
        ),
    )


# ── normalization tables & defaults ─────────────────────────────────────────────

# Axiell source code → FOLIO standard material-type name. Case-insensitive keys.
MATERIAL_TYPE: dict[str, str] = {
    "sound only": "sound recording",
    "audio-visual material - visual": "video recording",
    "audio-visual material - e-sound only": "sound recording",
    "audio-visual material - e-visual only": "video recording",
    "published material": "book",
    "archives": "unspecified",
}

# Fallbacks used when the MARC record carries no value for a resolved field.
DEFAULT_MATERIAL_TYPE = "book"
DEFAULT_LOAN_TYPE = "Can Circulate"
DEFAULT_LOCATION = "History of Medicine"
DEFAULT_HOLDINGS_SOURCE = "MARC"
AXIELL_LOCATION_NOTE_TYPE = "Axiell location"


def _instance_hrid(source_id: str) -> str:
    return f"AxC-instance-{source_id}"


def _holdings_hrid(source_id: str) -> str:
    return f"AxC-holding-{source_id}"


def _item_hrid(source_id: str) -> str:
    return f"AxC-item-{source_id}"


# ── payload models (the FOLIO contract) ─────────────────────────────────────────


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


class ElectronicAccess(BaseModel):
    uri: str


class Instance(BaseModel):
    model_config = ConfigDict(extra="forbid")  # guard against typo'd keys in our code
    hrid: str
    title: str
    source: str = "FOLIO"
    instanceTypeId: str


class Holdings(BaseModel):
    model_config = ConfigDict(extra="forbid")
    hrid: str
    instanceId: str | None = None  # injected by the upsert orchestrator
    sourceId: str
    permanentLocationId: str
    callNumber: str | None = None
    callNumberPrefix: str | None = None
    shelvingOrder: str | None = None


class Item(BaseModel):
    model_config = ConfigDict(extra="forbid")
    hrid: str
    holdingsRecordId: str | None = None  # injected by the upsert orchestrator
    status: Status = Field(default_factory=Status)
    materialType: IdRef
    permanentLoanType: IdRef
    permanentLocation: IdRef
    barcode: str | None = None
    copyNumber: str | None = None
    volume: str | None = None
    electronicAccess: list[ElectronicAccess] | None = None
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
    """The complete output of :func:`build_payloads`."""

    instance: Instance
    holdings: Holdings
    item: Item
    meta: PayloadMeta


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


# ── lookup helper (mirrors the YAML map → default → lookup chain) ────────────────


def _resolve(
    resolver: Callable[[str | None], str | None],
    raw: str | None,
    *,
    label: str,
    default: str,
    table: dict[str, str] | None = None,
) -> str:
    """Normalize a raw AxC code, fall back to ``default``, then resolve to a UUID.

    Raises MappingError if the resolved name is unknown to the FOLIO tenant.
    """
    value = (raw or "").strip()
    if table:
        value = table.get(value.lower(), value)
    if not value:
        value = default
    uuid = resolver(value)
    if uuid is None:
        raise MappingError(
            f"Unresolved {label} {value!r} — add it to the FOLIO tenant or fix the MARC"
        )
    return uuid


# ── builders (the mapping) ──────────────────────────────────────────────────────


def build_instance(rec: CanonicalRecord, ref: RefCache) -> Instance:
    if not rec.title:
        raise MappingError(f"Missing 245$a (title) for source_id={rec.source_id}")
    return Instance(
        hrid=_instance_hrid(rec.source_id),
        title=rec.title.strip(),
        # FOLIO-native: we create the instance via the Inventory API with no
        # linked SRS MARC record, so the source is FOLIO, not MARC.
        source="FOLIO",
        instanceTypeId=ref.instance_type_id(),
    )


def build_holdings(rec: CanonicalRecord, ref: RefCache) -> Holdings:
    return Holdings(
        hrid=_holdings_hrid(rec.source_id),
        sourceId=_resolve(
            ref.resolve_holdings_source,
            DEFAULT_HOLDINGS_SOURCE,
            label="holdings source",
            default=DEFAULT_HOLDINGS_SOURCE,
        ),
        permanentLocationId=_resolve(
            ref.resolve_location,
            rec.location_code,
            label="location",
            default=DEFAULT_LOCATION,
        ),
        callNumber=rec.call_number,
        callNumberPrefix=rec.call_number_prefix,
        shelvingOrder=rec.shelving_order,
    )


def build_item(rec: CanonicalRecord, ref: RefCache) -> Item:
    location_id = _resolve(
        ref.resolve_location,
        rec.location_code,
        label="location",
        default=DEFAULT_LOCATION,
    )
    return Item(
        hrid=_item_hrid(rec.source_id),
        materialType=IdRef(
            id=_resolve(
                ref.resolve_material_type,
                rec.material_type_code,
                label="material type",
                default=DEFAULT_MATERIAL_TYPE,
                table=MATERIAL_TYPE,
            )
        ),
        permanentLoanType=IdRef(
            id=_resolve(
                ref.resolve_loan_type,
                rec.loan_type_code,
                label="loan type",
                default=DEFAULT_LOAN_TYPE,
            )
        ),
        permanentLocation=IdRef(id=location_id),
        barcode=rec.barcode,
        copyNumber=rec.copy_number,
        volume=rec.volume,
        electronicAccess=(
            [ElectronicAccess(uri=rec.electronic_access_uri)]
            if rec.electronic_access_uri
            else None
        ),
        notes=[
            Note(
                note=f"Axiell location: {rec.location_code or 'unknown'}",
                noteType=AXIELL_LOCATION_NOTE_TYPE,
                staffOnly=False,
            )
        ],
    )


# ── orchestration ───────────────────────────────────────────────────────────────


def build_payloads(
    xml_content: str,
    ref_cache: RefCache,
    *,
    deleted: bool = False,
) -> MappedPayloads:
    """Parse MARCXML and build the three FOLIO payloads.

    Returns a :class:`MappedPayloads` model consumed by
    :func:`adapters.steps.axiell_folio_sync.upsert.upsert_from_payloads`.

    Raises :class:`MappingError` for required-field violations or unresolved lookups.
    """
    rec = parse_marcxml(xml_content, deleted=deleted)

    instance = build_instance(rec, ref_cache)
    holdings = build_holdings(rec, ref_cache)
    item = build_item(rec, ref_cache)

    return MappedPayloads(
        instance=instance,
        holdings=holdings,
        item=item,
        meta=PayloadMeta(
            source_id=rec.source_id,
            instance_hrid=_instance_hrid(rec.source_id),
            holdings_hrid=_holdings_hrid(rec.source_id),
            item_hrid=_item_hrid(rec.source_id),
            mapping_version=VERSION,
            deleted=deleted,
        ),
    )
