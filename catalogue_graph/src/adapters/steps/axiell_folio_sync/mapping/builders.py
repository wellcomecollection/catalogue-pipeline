"""
The mapping logic: MARC extraction, FieldMap resolution, and payload builders.

``select_and_build`` parses a MARCXML string into a :class:`CanonicalRecord`,
resolves each :class:`FieldMap` against the FOLIO tenant, and assembles the three
FOLIO payloads consumed by
:func:`adapters.steps.axiell_folio_sync.upsert.upsert_from_payloads`.
"""

from __future__ import annotations

from collections.abc import Callable

from pymarc.record import Record

from ..folio import RefCache
from .config import (
    AXIELL_LOCATION_NOTE_TYPE,
    HOLDINGS_SOURCE_FIELD,
    LOAN_TYPE_FIELD,
    LOCAL_IDENTIFIER_FIELD,
    LOCATION_FIELD,
    MARC_SOURCE,
    MATERIAL_TYPE_FIELD,
    RECORD_TYPE_ITEM,
    VERSION,
    FieldMap,
    _folio_location,
    _holdings_hrid,
    _instance_hrid,
    _item_hrid,
)
from .marc import CanonicalRecord, MappingError, extract, parse_xml
from .payloads import (
    Holdings,
    Identifier,
    IdRef,
    Instance,
    Item,
    MappedPayloads,
    Note,
    PayloadMeta,
)

# ── extraction ──────────────────────────────────────────────────────────────────


def _extract_record(root: Record, *, deleted: bool) -> CanonicalRecord:
    """Build a :class:`CanonicalRecord` from an already-parsed MARC record via MARC_SOURCE.

    Raises :class:`MappingError` if MARC 001 is absent (record cannot be identified).
    """
    values = {field: extract(root, spec) for field, spec in MARC_SOURCE.items()}

    # Pop the guaranteed-present source_id so it is passed explicitly (narrowed to
    # str after the guard); the remaining values are all the optional str | None fields.
    source_id = values.pop("source_id")
    if not source_id:
        raise MappingError("Missing MARC 001 — cannot identify record")

    return CanonicalRecord(
        source_id=source_id,
        deleted=deleted,
        **values,
    )


# ── lookup helper (mirrors the map → default → lookup chain) ─────────────────────


def _resolve(field: FieldMap, rec: CanonicalRecord, ref: RefCache) -> str:
    """Resolve one :class:`FieldMap` against a record to a FOLIO UUID.

    Reads the raw AxC value from ``rec`` (or falls back to the field default when
    there is no AxC source), applies the location prefix rules and normalization
    ``table``, then looks the resulting name up in the FOLIO tenant.

    Raises :class:`MappingError` if the resolved name is unknown to the tenant.
    """
    raw = getattr(rec, field.canonical) if field.canonical else None
    value = (raw or "").strip()
    if field.location:
        value = (_folio_location(value) or "").strip()
    if field.table:
        value = field.table.get(value.lower(), value)
    if not value:
        value = field.default or ""
    if field.resolver is None:
        raise MappingError(f"FieldMap {field.canonical!r} has no resolver")
    resolver: Callable[[str | None], str | None] = getattr(ref, field.resolver)
    uuid = resolver(value)
    if uuid is None:
        raise MappingError(
            f"Unresolved {field.label} {value!r} — add it to the FOLIO tenant or fix the MARC"
        )
    return uuid


# ── builders (the mapping) ──────────────────────────────────────────────────────


def build_instance(rec: CanonicalRecord, ref: RefCache) -> Instance:
    if not rec.title:
        raise MappingError(f"Missing 245$a (title) for source_id={rec.source_id}")
    # AxC object_number (the (AltRefNo)-prefixed 035$a) becomes a "Local identifier"
    # on the instance. Absent when the record carries no (AltRefNo) 035$a — omit
    # identifiers entirely then.
    identifiers = None
    object_number = (rec.object_number or "").strip()
    if object_number:
        identifiers = [
            Identifier(
                identifierTypeId=_resolve(LOCAL_IDENTIFIER_FIELD, rec, ref),
                value=object_number,
            )
        ]
    return Instance(
        hrid=_instance_hrid(rec.source_id),
        title=rec.title.strip(),
        # FOLIO-native: we create the instance via the Inventory API with no
        # linked SRS MARC record, so the source is FOLIO, not MARC.
        source="FOLIO",
        instanceTypeId=ref.instance_type_id(),
        identifiers=identifiers,
    )


def build_holdings(rec: CanonicalRecord, ref: RefCache) -> Holdings:
    return Holdings(
        hrid=_holdings_hrid(rec.source_id),
        sourceId=_resolve(HOLDINGS_SOURCE_FIELD, rec, ref),
        permanentLocationId=_resolve(LOCATION_FIELD, rec, ref),
    )


def build_item(rec: CanonicalRecord, ref: RefCache) -> Item:
    return Item(
        hrid=_item_hrid(rec.source_id),
        materialType=IdRef(id=_resolve(MATERIAL_TYPE_FIELD, rec, ref)),
        permanentLoanType=IdRef(id=_resolve(LOAN_TYPE_FIELD, rec, ref)),
        permanentLocation=IdRef(id=_resolve(LOCATION_FIELD, rec, ref)),
        barcode=rec.barcode,
        notes=[
            Note(
                note=rec.current_location or "unknown",
                noteType=AXIELL_LOCATION_NOTE_TYPE,
                staffOnly=False,
            )
        ],
    )


# ── orchestration ───────────────────────────────────────────────────────────────


def _assemble_payloads(
    rec: CanonicalRecord, ref_cache: RefCache, *, deleted: bool
) -> MappedPayloads:
    """Build the three FOLIO payloads + meta from an already-extracted record."""
    return MappedPayloads(
        instance=build_instance(rec, ref_cache),
        holdings=build_holdings(rec, ref_cache),
        item=build_item(rec, ref_cache),
        meta=PayloadMeta(
            source_id=rec.source_id,
            instance_hrid=_instance_hrid(rec.source_id),
            holdings_hrid=_holdings_hrid(rec.source_id),
            item_hrid=_item_hrid(rec.source_id),
            mapping_version=VERSION,
            deleted=deleted,
        ),
    )


def is_selected_for_sync(xml_content: str) -> bool:
    """Whether a record should be synced to FOLIO.

    True only for item-level records (MARC ``351 $c`` == "ITEM", case-insensitive);
    everything else is skipped entirely — never created, updated or suppressed.

    NOTE: the ``980 $a`` harvest-flag gate is removed for now ("run for all"), so
    selection is item-level only. To re-enable it, add the ``980 $a`` check back
    here and in ``select_and_build``.
    """
    root = parse_xml(xml_content)
    record_type = (extract(root, "351$c") or "").strip().upper()
    return record_type == RECORD_TYPE_ITEM


def select_and_build(
    xml_content: str,
    ref_cache: RefCache,
    *,
    deleted: bool = False,
) -> MappedPayloads | None:
    """Select and build in one pass — parses XML only once.

    Returns ``None`` when the record is not item-level (MARC ``351$c`` != "ITEM").
    Raises on malformed XML or mapping errors.
    """
    root = parse_xml(xml_content)

    # Selection gate: only sync item-level records.
    record_type = (extract(root, "351$c") or "").strip().upper()
    if record_type != RECORD_TYPE_ITEM:
        return None

    rec = _extract_record(root, deleted=deleted)
    return _assemble_payloads(rec, ref_cache, deleted=deleted)
