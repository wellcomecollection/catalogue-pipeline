"""
AxC → FOLIO mapping configuration: the declarative "what maps to what".

This module holds only *data* — no logic that touches a MARC record or a FOLIO
tenant. The single source of truth is the :data:`FIELDS` table: each
:class:`FieldMap` row ties one field's inbound MARC source to its outbound FOLIO
resolution. The builders (see ``builders.py``) read from this — selection and
extraction both live there too.

RFC 090 references below point at the design spec:
https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync
"""

from __future__ import annotations

from dataclasses import dataclass

# Bumped whenever the mapping rules change; stamped into every payload's meta.
VERSION = "2.6.0"


# ── record selection (RFC 090 §Record selection) ─────────────────────────────
# A record is synced to FOLIO only if it is flagged for harvest AND is item-level.
# Both are read from the harvested MARCXML.
HARVEST_FLAG_SPEC = "980$a"  # present (non-empty) = opted in for FOLIO sync
RECORD_TYPE_ITEM = "ITEM"  # only item-level records are synced


# ── normalization tables & defaults ─────────────────────────────────────────────

# Axiell object_category (MARC 655$a) → FOLIO standard material-type name.
# Keys carry the AxC object_category value in its real (mixed) case for readability;
# matching is case-insensitive — MATERIAL_TYPE_FIELD folds these keys to lowercase
# and `_resolve` lowercases the incoming value before lookup.
# Values reflect the AxC object_category mappings agreed in the Folio Axiell Mapping.
MATERIAL_TYPE: dict[str, str] = {
    "Archives - Non Digital": "archive",
    "Archives - Non-digital": "archive",
    "Moving Image - Non Digital": "film",
    "Moving Image - Non-digital": "film",
    "Sound - Non Digital": "audio format requestable",
    "Sound - Non-digital": "audio format requestable",
    "Visual Material - Non Digital": "non-projected graphic",
    "Visual Material - Non-digital": "non-projected graphic",
}

# Fallbacks used when the MARC record carries no value for a resolved field.
DEFAULT_MATERIAL_TYPE = "book"
DEFAULT_LOAN_TYPE = "Can circulate"
DEFAULT_LOCATION = "History of Medicine"
DEFAULT_HOLDINGS_SOURCE = "MARC"
AXIELL_LOCATION_NOTE_TYPE = "Axiell location"
# FOLIO instance identifier type used for the AxC object_number
# (the (AltRefNo)-prefixed 035$a).
LOCAL_IDENTIFIER_TYPE = "Local identifier"

# AxC current_location (MARC 852$b) values whose leading digits map to a fixed
# FOLIO location, overriding the normal code/name lookup. First match wins.
LOCATION_PREFIX_OVERRIDES: dict[str, tuple[str, ...]] = {
    "hicon": ("215", "183"),
}


def _folio_location(current_location: str | None) -> str | None:
    """Resolve an AxC current_location to the FOLIO location to look up.

    Applies the :data:`LOCATION_PREFIX_OVERRIDES` prefix rules; when none match,
    returns ``current_location`` unchanged for the normal code/name → default chain.
    """
    value = (current_location or "").strip()
    for folio_location, prefixes in LOCATION_PREFIX_OVERRIDES.items():
        if value.startswith(prefixes):
            return folio_location
    return current_location


def _instance_hrid(source_id: str) -> str:
    return f"AxC-instance-{source_id}"


def _holdings_hrid(source_id: str) -> str:
    return f"AxC-holding-{source_id}"


def _item_hrid(source_id: str) -> str:
    return f"AxC-item-{source_id}"


# ── the AxC ⇄ FOLIO field map (single source of truth) ──────────────────────────
#
# One :class:`FieldMap` row ties both sides of a single field together:
#
#   • inbound  — the MARC ``spec`` whose value populates ``CanonicalRecord.<canonical>``
#   • outbound — how that value is turned into a FOLIO UUID (resolver / default /
#                normalization ``table``)
#
# Resolved fields (value → FOLIO tenant UUID) are named constants so the builders
# reference the exact same row they are extracted from; plain passthrough fields
# (title, barcode, …) that need no tenant lookup appear inline in ``FIELDS``.


@dataclass(frozen=True)
class FieldMap:
    """One AxC → FOLIO field mapping — the inbound MARC source and the outbound
    FOLIO resolution declared together.

    ``marc`` is ``None`` for a FOLIO field with no AxC source (resolved from a
    constant, e.g. the holdings source). ``resolver`` is ``None`` for a plain
    passthrough field (title, barcode) that needs no FOLIO tenant lookup.
    """

    canonical: str | None  # CanonicalRecord attribute name (None = no AxC source)
    marc: str | None = None  # MARC source spec: "TAG$sub" (datafield) / "TAG" (control)
    resolver: str | None = None  # RefCache method name: AxC value → FOLIO UUID
    default: str | None = None  # fallback when the record carries no value
    label: str | None = None  # human label used in MappingError messages
    table: dict[str, str] | None = None  # AxC-code → FOLIO-name normalization
    location: bool = False  # apply LOCATION_PREFIX_OVERRIDES before resolving


# Resolved fields → FOLIO tenant UUIDs. Referenced by both FIELDS (extraction)
# and the builders (resolution), so source and target never drift apart.
MATERIAL_TYPE_FIELD = FieldMap(
    "object_category",
    marc="655$a",
    resolver="resolve_material_type",
    default=DEFAULT_MATERIAL_TYPE,
    label="material type",
    # Fold keys to lowercase so the case-insensitive lookup in `_resolve` (which
    # lowercases the incoming AxC value) matches whatever case AxC sends.
    table={key.lower(): value for key, value in MATERIAL_TYPE.items()},
)
LOCATION_FIELD = FieldMap(
    "current_location",
    marc="852$b",
    resolver="resolve_location",
    default=DEFAULT_LOCATION,
    label="location",
    location=True,
)
LOAN_TYPE_FIELD = FieldMap(
    "loan_type_code",
    marc="949$l",
    resolver="resolve_loan_type",
    default=DEFAULT_LOAN_TYPE,
    label="loan type",
)
# holdings.sourceId is resolved from a constant, not from any AxC field.
HOLDINGS_SOURCE_FIELD = FieldMap(
    None,
    resolver="resolve_holdings_source",
    default=DEFAULT_HOLDINGS_SOURCE,
    label="holdings source",
)
# instance.identifiers[].identifierTypeId is a constant ("Local identifier"); the
# identifier *value* passes through from object_number (the (AltRefNo)-prefixed
# 035$a) in build_instance.
LOCAL_IDENTIFIER_FIELD = FieldMap(
    None,
    resolver="resolve_identifier_type",
    default=LOCAL_IDENTIFIER_TYPE,
    label="identifier type",
)

# The full AxC-source table. Resolved rows are the named constants above; the
# plain rows below are extracted straight onto the CanonicalRecord.
FIELDS: tuple[FieldMap, ...] = (
    FieldMap("source_id", marc="001"),  # Axiell GUID — identifies the record
    FieldMap("title", marc="245$a"),  # → instance.title
    FieldMap(
        "object_number", marc="035$a(AltRefNo)"
    ),  # (AltRefNo)-prefixed 035$a → instance.identifiers (local identifier)
    MATERIAL_TYPE_FIELD,  # → item.materialType
    LOCATION_FIELD,  # → holdings/item location + Axiell location note
    FieldMap("barcode", marc="949$a"),  # → item.barcode
    LOAN_TYPE_FIELD,  # → item.permanentLoanType
)

# Inbound extraction map derived from FIELDS: CanonicalRecord attr → MARC spec.
# Spec syntax: "TAG$subfield" for datafields, "TAG" for controlfields.
MARC_SOURCE: dict[str, str] = {
    f.canonical: f.marc for f in FIELDS if f.canonical and f.marc
}
