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

# Axiell object_category (MARC 655$a) to FOLIO material type. Matching is
# case-insensitive but not whitespace-insensitive, so the key has to be the exact
# string AxC sends.
#
# Every key here is a value that actually occurs in the corpus, and every target
# exists in the FOLIO tenant, so nothing needs provisioning. The table covers
# 187,882 of 187,997 item records; the remaining 115 carry no 655$a and fail,
# because there is no default (see MATERIAL_TYPE_FIELD).
#
# The digital rows are the ones to confirm with Collection Information, because
# this tenant encodes requestability in the material type: a digital surrogate is
# not the physical carrier a reader requests, hence the non-requestable halves.
# See docs/axiell-folio-mapping-options.md section 1.
MATERIAL_TYPE: dict[str, str] = {
    "Archives - Non-digital": "archive",
    "Archives - Digital": "archive",
    "Archives - Hybrid": "archive",
    "Moving Image - Non-digital": "film",
    "Moving Image - Digital": "video format non-requestable",
    "Sound - Non-digital": "audio format requestable",
    "Sound - Digital": "audio format non-requestable",
    # AxC says plain "Visual Material", never "Visual Material - Non-digital".
    # The old key was a near-miss, so the agreed non-projected graphic rule had
    # never once fired.
    "Visual Material": "non-projected graphic",
    "Pictures": "non-projected graphic",
}


# Map Axiell access categories (MARC 506$f) to FOLIO item statuses.
# Values are FOLIO's fixed item-status values and need no resolver.
# Confirm "Restricted" and "Withdrawn" against the tenant's FOLIO version;
# see rfcs/collection-information-questions.md section 6.
ACCESS_ITEM_STATUS: dict[str, str] = {
    "OPEN": "Available",
    "OPENWITHADVISORY": "Available",
    # Available, not Restricted. Restricted material is genuinely available and
    # can be requested online. The restriction is that the reader signs to agree
    # to the conditions of viewing restricted material, and they do that before
    # the material is handed over, so it does not affect whether the item can be
    # requested or produced.
    "RESTRICTED": "Available",
    "PERMISSIONREQUIRED": "Restricted",
    "SAFEGUARDED": "Restricted",
    "CLOSED": "Restricted",
    "MISSING": "Missing",
    "DEACCESSIONED": "Withdrawn",
    # The record's own data is known to be wrong, so the item's real state is
    # not known. "Unknown" says that, where "Unavailable" would assert something
    # about the item that nobody has established.
    "DATAISSUES": "Unknown",
}

# Fallbacks used when the MARC record carries no value for a resolved field.
#
# The material type has none. It used to default to "book", which was wrong for
# every record it applied to: this is an archival corpus and none of them is a
# book. A record with no 655$a now fails and is reported, rather than being given
# a plausible-looking wrong type that nothing surfaces. See MATERIAL_TYPE_FIELD.
# The default loan type, and currently the only one any item gets: no AxC field
# is mapped to the loan type, so nothing ever overrides this.
#
# AxC has two candidate sources, and it is not settled which should drive this or
# whether open archival material should circulate at all. The access category
# (506$f) was mapped here for a time, and so was the use restriction (540$a).

DEFAULT_LOAN_TYPE = "Can circulate"
# Same principle for the item status: no access category means the item is not
# presented as available. A category that is present but unrecognised does not
# reach this default either. _item_status raises instead.
DEFAULT_ITEM_STATUS = "Unavailable"
DEFAULT_HOLDINGS_SOURCE = "MARC"
# Prefix for AxC's current location (852$b) in administrativeNotes. It identifies
# the note for replacement on updates. If changed after syncing begins, continue
# matching the old label to avoid orphaning or duplicating existing notes.
AXIELL_LOCATION_NOTE_PREFIX = "Axiell Current Location"
# FOLIO instance identifier type used for the AxC object_number
# (the (AltRefNo)-prefixed 035$a).
LOCAL_IDENTIFIER_TYPE = "Local identifier"

# AxC current_location (MARC 852$b) codes that map to fixed FOLIO locations.
# FOLIO's location hierarchy is institution → campus → library → location, but the
# sync resolves a single *leaf* location UUID: RefCache indexes locations by code
# and name only, and a leaf implies its parents. The parent names are recorded on
# Parent names document the agreed hierarchy but are not used for lookup.
FOLIO_INSTITUTION = "Wellcome Collection"


@dataclass(frozen=True)
class LocationRule:
    """One AxC normal-location → FOLIO location rule.

    Matching is on the *leading code* of the AxC location hierarchy — see
    :func:`_folio_location` for why that is the unit, rather than a prefix of the
    whole string. ``codes`` match that code exactly; ``prefixes`` match its start.
    """

    location: str  # FOLIO leaf location name — the only part that is resolved
    campus: str
    library: str
    codes: tuple[str, ...] = ()  # leading code, matched exactly
    prefixes: tuple[str, ...] = ()  # leading code, matched by prefix


# The agreed AxC → FOLIO location mapping. First match wins.
LOCATION_RULES: tuple[LocationRule, ...] = (
    LocationRule(
        codes=("215", "183"),
        location="AxC Euston Road",
        campus="Euston Road (Axiell)",
        library="Axiell sync",
    ),
    LocationRule(
        codes=("Deepstore",),
        location="AxC Deepstore",
        campus="Deepstore",
        library="Offsite (DS)",
    ),
    LocationRule(
        prefixes=("CLW",),
        location="AxC Constantine London West",
        campus="Constantine London West",
        library="Axiell sync",
    ),
)


def _leading_location_code(context: str | None) -> str:
    """The first code of an AxC location hierarchy path.

    AxC nests the hierarchy two ways at once: the path is "/"-separated and each
    segment is a ";"-separated code, so a real 984$b looks like
    ``"215/215;B11/215;B11;MR/…"`` and its leaf (984$c) like ``"215;B11;MR;84"``.
    Taking the first component of each split yields ``"215"`` from either spelling.
    """
    return (context or "").strip().split("/")[0].split(";")[0].strip()


def _folio_location(location: str | None) -> str | None:
    """Resolve an AxC location to the FOLIO leaf location name to look up.

    Matches :data:`LOCATION_RULES` against the *leading code* rather than against
    the raw string: the codes are the hierarchy's own units, so ``"215"`` cannot
    also swallow ``"2150"`` or ``"215A"``, which a bare ``startswith("215")``
    would. When no rule matches, the value is returned unchanged so it can still
    resolve as a FOLIO code or name — and, failing that, be reported as an
    unresolved location rather than quietly shelved somewhere plausible.
    """
    code = _leading_location_code(location)
    if code:
        for rule in LOCATION_RULES:
            if code in rule.codes or (rule.prefixes and code.startswith(rule.prefixes)):
                return rule.location
    return location


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
# (title and so on) that need no tenant lookup appear inline in ``FIELDS``.


@dataclass(frozen=True)
class FieldMap:
    """One AxC → FOLIO field mapping — the inbound MARC source and the outbound
    FOLIO resolution declared together.

    ``marc`` is ``None`` for a FOLIO field with no AxC source (resolved from a
    constant, e.g. the holdings source). ``resolver`` is ``None`` for a plain
    passthrough field (title) that needs no FOLIO tenant lookup.
    ``required`` and ``default`` are mutually exclusive: a field either has
    something to fall back on or it fails when the record carries no value.
    """

    canonical: str | None  # CanonicalRecord attribute name (None = no AxC source)
    marc: str | None = None  # MARC source spec: "TAG$sub" (datafield) / "TAG" (control)
    resolver: str | None = None  # RefCache method name: AxC value → FOLIO UUID
    default: str | None = None  # fallback when the record carries no value
    label: str | None = None  # human label used in MappingError messages
    table: dict[str, str] | None = None  # AxC-code → FOLIO-name normalization
    location: bool = False  # apply LOCATION_RULES before resolving
    required: bool = False  # no value → MappingError, instead of a default


# Resolved fields → FOLIO tenant UUIDs. Referenced by both FIELDS (extraction)
# and the builders (resolution), so source and target never drift apart.
MATERIAL_TYPE_FIELD = FieldMap(
    "object_category",
    marc="655$a",
    resolver="resolve_material_type",
    label="material type",
    # Required, with no default. An AxC category that is present but unmapped
    # already failed the record, because the raw value resolves to nothing in the
    # tenant; this makes an absent category behave the same way instead of
    # silently typing it "book".
    required=True,
    # Fold keys to lowercase so the case-insensitive lookup in `_resolve` (which
    # lowercases the incoming AxC value) matches whatever case AxC sends.
    table={key.lower(): value for key, value in MATERIAL_TYPE.items()},
)
# The AxC *current* location. Extraction only: it feeds the administrative note
# verbatim and is no longer resolved to a FOLIO location UUID — the shelf location
# comes from NORMAL_LOCATION_FIELD below. Hence no resolver and no default.
CURRENT_LOCATION_FIELD = FieldMap(
    "current_location",
    marc="852$b",
)
# AxC normal location maps to FOLIO permanentLocation. The XSLT writes it to
# local field 984 ($b), which avoids 983 because that field already carries part
# references; 984 is otherwise unused in this harvest.
#
# `required=True` means missing or unknown normal locations fail the record
# instead of silently creating a plausible but wrong default location.
NORMAL_LOCATION_FIELD = FieldMap(
    "normal_location",
    marc="984$b",
    resolver="resolve_location",
    label="normal location",
    location=True,
    required=True,
)
# item.permanentLoanType is resolved from a constant, not from any AxC field.
# See DEFAULT_LOAN_TYPE above for why, and for what it costs.
LOAN_TYPE_FIELD = FieldMap(
    None,
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
    CURRENT_LOCATION_FIELD,  # → the Axiell Current Location admin note (verbatim)
    NORMAL_LOCATION_FIELD,  # → holdings.permanentLocationId + item.permanentLocation
    FieldMap("access_category", marc="506$f"),  # → item.status, via ACCESS_ITEM_STATUS
)

# Inbound extraction map derived from FIELDS: CanonicalRecord attr → MARC spec.
# Spec syntax: "TAG$subfield" for datafields, "TAG" for controlfields.
MARC_SOURCE: dict[str, str] = {
    f.canonical: f.marc for f in FIELDS if f.canonical and f.marc
}
