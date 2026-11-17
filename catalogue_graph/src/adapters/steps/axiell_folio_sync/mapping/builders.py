"""
The mapping logic: MARC extraction, FieldMap resolution, and payload builders.

``select_and_build`` parses a MARCXML string into a :class:`CanonicalRecord`,
resolves each :class:`FieldMap` against the FOLIO tenant, and assembles the three
FOLIO payloads consumed by
:func:`adapters.steps.axiell_folio_sync.upsert.upsert_from_payloads`.
"""

from __future__ import annotations

import dataclasses
from collections.abc import Callable
from typing import Literal

from pymarc.record import Record

from ..folio import RefCache
from .config import (
    ACCESS_ITEM_STATUS,
    ACCESS_STATISTICAL_CODE,
    ACCESS_STATISTICAL_CODE_FIELD,
    ACCESS_STATISTICAL_CODES,
    AXIELL_LOCATION_NOTE_PREFIX,
    CURRENT_LOCATION_NOTE_TYPE_FIELD,
    DEFAULT_ITEM_STATUS,
    HARVEST_FLAG_SPEC,
    HOLDINGS_SOURCE_FIELD,
    LOAN_TYPE_FIELD,
    LOCAL_IDENTIFIER_FIELD,
    MARC_SOURCE,
    MATERIAL_TYPE_FIELD,
    NORMAL_LOCATION_FIELD,
    NORMAL_LOCATION_NOTE_TYPE_FIELD,
    RECORD_TYPE_ITEM,
    SYNCED_STATISTICAL_CODE_FIELD,
    VERSION,
    FieldMap,
    _folio_location,
    _guid,
    _guid_label,
    _holdings_id,
    _instance_id,
    _item_id,
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
    Status,
)

# Which selection gate a record passes or fails, from :func:`_selection`.
Selection = Literal["selected", "unflagged", "not_item_level"]


# ── extraction ──────────────────────────────────────────────────────────────────


def _extract_record(root: Record, *, deleted: bool) -> CanonicalRecord:
    """Build a :class:`CanonicalRecord` from an already-parsed MARC record via MARC_SOURCE.

    Raises :class:`MappingError` if MARC 001 is absent or is not a UUID, since
    the FOLIO ids are derived from it (record cannot be identified).
    """
    values = {field: extract(root, spec) for field, spec in MARC_SOURCE.items()}

    # Pop the guaranteed-present source_id so it is passed explicitly (narrowed to
    # str after the guard); the remaining values are all the optional str | None fields.
    source_id = values.pop("source_id")
    if not source_id:
        raise MappingError("Missing MARC 001 — cannot identify record")
    try:
        _guid(source_id)
    except ValueError:
        raise MappingError(
            f"MARC 001 is not a UUID, so no FOLIO id can be derived: {source_id!r}"
        ) from None

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

    Raises :class:`MappingError` if the resolved name is unknown to the tenant,
    or if the field sets ``strict_table`` and the value is not in its table.
    """
    raw = getattr(rec, field.canonical) if field.canonical else None
    value = (raw or "").strip()
    if field.required and not value:
        raise MappingError(
            f"Missing {field.label} (MARC {field.marc}) for source_id={rec.source_id}"
            " — required, with no default to fall back on"
        )
    if field.location:
        value = (_folio_location(value) or "").strip()
    if field.table:
        mapped = field.table.get(value.lower())
        if mapped is None and value and field.strict_table:
            # Checked before the resolver, because the resolver would accept any
            # name the tenant happens to carry and the table is the agreed
            # vocabulary. Without this an unmapped AxC value reaches FOLIO with
            # whatever semantics that name has there.
            raise MappingError(
                f"Unmapped {field.label} {value!r} (MARC {field.marc}) for"
                f" source_id={rec.source_id} — add it to the mapping table,"
                " which is the agreed vocabulary, rather than relying on the"
                " tenant to know the raw AxC value"
            )
        if mapped is None and field.unmapped_to_default:
            value = ""  # falls through to the default below
        value = value if mapped is None else mapped
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
        id=_instance_id(rec.source_id),
        title=rec.title.strip(),
        # FOLIO-native: we create the instance via the Inventory API with no
        # linked SRS MARC record, so the source is FOLIO, not MARC.
        source="FOLIO",
        instanceTypeId=ref.instance_type_id(),
        identifiers=identifiers,
        statisticalCodeIds=[_resolve(SYNCED_STATISTICAL_CODE_FIELD, rec, ref)],
    )


def build_holdings(rec: CanonicalRecord, ref: RefCache) -> Holdings:
    return Holdings(
        id=_holdings_id(rec.source_id),
        instanceId=_instance_id(rec.source_id),
        sourceId=_resolve(HOLDINGS_SOURCE_FIELD, rec, ref),
        permanentLocationId=_resolve(NORMAL_LOCATION_FIELD, rec, ref),
        formerIds=[_guid_label(rec.source_id)],
        statisticalCodeIds=[_resolve(SYNCED_STATISTICAL_CODE_FIELD, rec, ref)],
    )


def _item_status(rec: CanonicalRecord) -> Status:
    """The FOLIO item status, from the AxC access category.

    Statuses are fixed FOLIO enums, not tenant reference data. Missing
    categories use the default; unrecognised categories raise a mapping error.
    """
    category = (rec.access_category or "").strip()
    if not category:
        return Status(name=DEFAULT_ITEM_STATUS)
    name = ACCESS_ITEM_STATUS.get(category.upper())
    if name is None:
        raise MappingError(
            f"Unrecognised access category {category!r} for item status:"
            " add it to ACCESS_ITEM_STATUS or fix the MARC"
        )
    return Status(name=name)


def _location_notes(rec: CanonicalRecord, ref: RefCache) -> list[Note]:
    """The typed item notes carrying the raw AxC current and normal locations.

    Not staff-only, so the FOLIO enrichment endpoint can read them. Both are
    always emitted, current location as "unknown" when 852$b is absent, for the
    same reason as the administrative note: the upsert replaces notes of these
    types, and a note left out would leave the previous value in place. The
    normal location is required by the time this runs, so it is never absent.
    """
    return [
        Note(
            note=rec.current_location or "unknown",
            itemNoteTypeId=_resolve(CURRENT_LOCATION_NOTE_TYPE_FIELD, rec, ref),
            staffOnly=False,
        ),
        Note(
            note=rec.normal_location or "unknown",
            itemNoteTypeId=_resolve(NORMAL_LOCATION_NOTE_TYPE_FIELD, rec, ref),
            staffOnly=False,
        ),
    ]


def _access_statistical_code(rec: CanonicalRecord) -> str | None:
    """The access statistical code for the record's 506$f and 540$a, or None.

    None when either field is absent or the pair is not one of the agreed rows
    in ACCESS_STATISTICAL_CODE. A missing code does not fail the record: the
    item still syncs, without an access code, as it did before codes existed.
    """
    category = (rec.access_category or "").strip().upper()
    term = (rec.use_restriction or "").strip().casefold()
    by_term = ACCESS_STATISTICAL_CODE.get(category, {})
    for known, code in by_term.items():
        if known.casefold() == term:
            return code
    return None


def _item_statistical_codes(rec: CanonicalRecord, ref: RefCache) -> list[str]:
    """The synced marker, then the access code when the record maps to one."""
    codes = [_resolve(SYNCED_STATISTICAL_CODE_FIELD, rec, ref)]
    access = _access_statistical_code(rec)
    if access is not None:
        # Resolved like any constant, so a code the tenant lacks fails the record
        # rather than being dropped.
        field = dataclasses.replace(ACCESS_STATISTICAL_CODE_FIELD, default=access)
        access_id = _resolve(field, rec, ref)
        if access_id not in codes:
            codes.append(access_id)
    return codes


def _access_statistical_code_ids(rec: CanonicalRecord, ref: RefCache) -> list[str]:
    """The tenant ids of every access code the mapping can set.

    For the upsert, which replaces whichever of these the item already carries.
    A code the tenant does not have is left out: nothing can carry it. The
    synced marker is never among them, even if a tenant gave both one id.
    """
    marker = _resolve(SYNCED_STATISTICAL_CODE_FIELD, rec, ref)
    ids = {ref.resolve_statistical_code(code) for code in ACCESS_STATISTICAL_CODES}
    return sorted(code_id for code_id in ids if code_id and code_id != marker)


def build_item(rec: CanonicalRecord, ref: RefCache) -> Item:
    return Item(
        id=_item_id(rec.source_id),
        holdingsRecordId=_holdings_id(rec.source_id),
        materialType=IdRef(id=_resolve(MATERIAL_TYPE_FIELD, rec, ref)),
        status=_item_status(rec),
        permanentLoanType=IdRef(id=_resolve(LOAN_TYPE_FIELD, rec, ref)),
        permanentLocation=IdRef(id=_resolve(NORMAL_LOCATION_FIELD, rec, ref)),
        # The AxC current location is also kept as an administrative note, an
        # internal audit trail of where the item actually is, alongside the typed
        # note in `notes`. The label is part of the string because an
        # administrative note carries no type to identify it by.
        #
        # Always emitted, "unknown" included: the note is absent from the payload
        # otherwise, and _upsert_entity merges payload over the existing record,
        # so omitting it would leave a previous location in place rather than
        # clearing it.
        administrativeNotes=[
            f"{AXIELL_LOCATION_NOTE_PREFIX}: {rec.current_location or 'unknown'}"
        ],
        notes=_location_notes(rec, ref),
        statisticalCodeIds=_item_statistical_codes(rec, ref),
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
            mapping_version=VERSION,
            deleted=deleted,
            access_statistical_code_ids=_access_statistical_code_ids(rec, ref_cache),
        ),
    )


def is_selected_for_sync(xml_content: str) -> bool:
    """Whether a record should be synced to FOLIO.

    Two gates, both read from the harvested MARCXML, and both have to pass:

    1. the record is item-level (``351 $c`` == "ITEM", case-insensitive), and
    2. the harvest flag (``980 $a``) is present and non-empty, meaning the record
       is opted in to the FOLIO sync.

    A selected record is created or updated, and any suppression is cleared, so
    ticking Harvest again restores the FOLIO records unticking it suppressed
    (see :func:`unflagged_guid`). The ids derive from the GUID, so the sync
    re-attaches to the same records rather than creating new ones.

    An item-level record without the flag is suppressed in FOLIO, which is a
    no-op for one that never synced. A record that is not item-level is skipped:
    not created, not updated, not suppressed, and not counted as an error. That
    includes one that synced as an item and has since changed level.
    """
    root = parse_xml(xml_content)
    return _selection(root) == "selected"


def _selection(root: Record) -> Selection:
    """The selection gates, shared by :func:`is_selected_for_sync`,
    :func:`select_and_build` and :func:`unflagged_guid` so they can never
    disagree."""
    record_type = (extract(root, "351$c") or "").strip().upper()
    if record_type != RECORD_TYPE_ITEM:
        return "not_item_level"
    harvest_flag = (extract(root, HARVEST_FLAG_SPEC) or "").strip()
    if not harvest_flag:
        return "unflagged"
    return "selected"


def unflagged_guid(xml_content: str) -> str | None:
    """The GUID of an item-level record without the harvest flag, or None.

    These are the records whose FOLIO records are suppressed: Harvest has been
    unticked, or was never ticked, and the sync cannot tell which without
    looking in FOLIO. None for a selected record, one that is not item-level, or
    one whose 001 is not a UUID, which cannot have synced because no FOLIO id
    derives from it.
    """
    root = parse_xml(xml_content)
    if _selection(root) != "unflagged":
        return None
    source_id = (extract(root, MARC_SOURCE["source_id"]) or "").strip()
    try:
        _guid(source_id)
    except ValueError:
        return None
    return source_id


def select_and_build(
    xml_content: str,
    ref_cache: RefCache,
    *,
    deleted: bool = False,
) -> MappedPayloads | None:
    """Select and build in one pass — parses XML only once.

    Returns ``None`` when the record fails either selection gate: not
    item-level, or no ``980 $a`` harvest flag. Raises on malformed XML or mapping errors.
    """
    root = parse_xml(xml_content)

    if _selection(root) != "selected":
        return None

    rec = _extract_record(root, deleted=deleted)
    return _assemble_payloads(rec, ref_cache, deleted=deleted)
