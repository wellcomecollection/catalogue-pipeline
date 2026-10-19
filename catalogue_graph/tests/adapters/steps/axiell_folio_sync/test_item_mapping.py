"""The AxC locations become notes on the FOLIO item, and the item mapping rules."""

from __future__ import annotations

import pytest

from adapters.steps.axiell_folio_sync.mapping import (
    AXIELL_CURRENT_LOCATION_NOTE_TYPE,
    AXIELL_LOCATION_NOTE_PREFIX,
    AXIELL_NORMAL_LOCATION_NOTE_TYPE,
    MATERIAL_TYPE,
    USE_RESTRICTION_LOAN_TYPE,
    Item,
    MappingError,
    select_and_build,
)


def _item_record(
    current_location: str | None = "215;ROOM 3",
    normal_location: str | None = "215;HOME 1",
) -> str:
    """Minimal item-level MARCXML: optional 852 $b current location, and a 984 $b
    normal location, which the mapping requires."""
    fields = ""
    if current_location is not None:
        fields += (
            "<datafield tag='852'>"
            f"<subfield code='b'>{current_location}</subfield>"
            "</datafield>"
        )
    if normal_location is not None:
        fields += (
            "<datafield tag='984'>"
            f"<subfield code='b'>{normal_location}</subfield>"
            "</datafield>"
        )
    return (
        "<record>"
        "<controlfield tag='001'>guid-1</controlfield>"
        # 980 $a is the harvest flag; without it nothing is selected.
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield></datafield>"
        f"{fields}"
        "</record>"
    )


class FakeRefCache:
    """Resolves every reference-data name to a stub UUID (no FOLIO calls).

    The return types mirror the real ``RefCache``, whose resolvers all return
    ``str | None``. Narrowing them to ``str`` here would stop a subclass that
    returns ``None`` for an unknown name from overriding them, and returning
    ``None`` is exactly what several tests need in order to make ``_resolve``
    raise.
    """

    def instance_type_id(self) -> str:
        return "itype-uuid"

    def resolve_location(self, name: str | None) -> str | None:
        return "loc-uuid"

    def resolve_holdings_source(self, name: str | None) -> str | None:
        return "src-uuid"

    def resolve_material_type(self, name: str | None) -> str | None:
        return "mat-uuid"

    def resolve_loan_type(self, name: str | None) -> str | None:
        return "loan-uuid"

    def resolve_item_note_type(self, name: str | None) -> str | None:
        return "note-uuid"

    def resolve_identifier_type(self, name: str | None) -> str | None:
        return "idtype-uuid"


def _build_item(current_location: str | None = "215;ROOM 3") -> Item:
    mapped = select_and_build(_item_record(current_location), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None
    return mapped.item


def test_current_location_becomes_a_labelled_administrative_note() -> None:
    item = _build_item("215;ROOM 3")

    assert item.administrativeNotes == [f"{AXIELL_LOCATION_NOTE_PREFIX}: 215;ROOM 3"]


def test_administrative_note_is_written_even_when_852b_is_absent() -> None:
    """Always emitted: the upsert merges payload over the existing record, so
    omitting the note would leave a stale location in place rather than clear it.
    """
    item = _build_item(None)

    assert item.administrativeNotes == [f"{AXIELL_LOCATION_NOTE_PREFIX}: unknown"]


class NoteTypeRefCache(FakeRefCache):
    """Resolves each item note type to a UUID derived from its name, so a test
    can tell which type each note was given."""

    def resolve_item_note_type(self, name: str | None) -> str | None:
        return f"uuid-of:{name}"


def test_both_locations_become_typed_item_notes() -> None:
    mapped = select_and_build(
        _item_record("215;ROOM 3", "215;HOME 1"),
        NoteTypeRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None

    assert mapped.item.model_dump(exclude_none=True)["notes"] == [
        {
            "note": "215;ROOM 3",
            "itemNoteTypeId": f"uuid-of:{AXIELL_CURRENT_LOCATION_NOTE_TYPE}",
            "staffOnly": False,
        },
        {
            "note": "215;HOME 1",
            "itemNoteTypeId": f"uuid-of:{AXIELL_NORMAL_LOCATION_NOTE_TYPE}",
            "staffOnly": False,
        },
    ]


def test_the_current_location_note_is_written_even_when_852b_is_absent() -> None:
    """Always emitted, like the administrative note, so an update replaces a
    stale current location rather than leaving it in place."""
    item = _build_item(None)

    assert item.notes is not None
    assert item.notes[0].note == "unknown"


def test_the_administrative_note_is_kept_alongside_the_typed_notes() -> None:
    item = _build_item("215;ROOM 3")

    assert item.administrativeNotes == [f"{AXIELL_LOCATION_NOTE_PREFIX}: 215;ROOM 3"]
    assert item.notes is not None
    assert len(item.notes) == 2


@pytest.mark.parametrize(
    "missing", [AXIELL_CURRENT_LOCATION_NOTE_TYPE, AXIELL_NORMAL_LOCATION_NOTE_TYPE]
)
def test_a_note_type_unknown_to_the_tenant_fails(missing: str) -> None:
    """An untyped note could not be recognised on the next update, so it would
    be duplicated every run. Failing the record is the safer outcome."""

    class MissingNoteTypeRefCache(FakeRefCache):
        def resolve_item_note_type(self, name: str | None) -> str | None:
            return None if name == missing else "note-uuid"

    with pytest.raises(MappingError, match="Unresolved item note type"):
        select_and_build(_item_record(), MissingNoteTypeRefCache())  # type: ignore[arg-type]


# ── AxC normal (home) location, MARC 984 ──────────────────────────────────────
#
# The XSLT (axiell-collections-xslt, axc_to_marcxml_*.xsl) emits the current
# location to 852 and the normal one to local field 984, same subfields in both:
# $b the full hierarchy path, $c the leaf. A separate tag rather than a second
# 852, because `extract()` selects by tag and subfield only — a second 852 would
# be read as the current location whenever it came first.


def _record_with_locations(
    current: str | None = "215/B11/Box 3", normal: str | None = "215/HOME"
) -> str:
    fields = ""
    if current is not None:
        fields += (
            f"<datafield tag='852'><subfield code='b'>{current}</subfield>"
            "<subfield code='c'>Box 3</subfield></datafield>"
        )
    if normal is not None:
        fields += (
            f"<datafield tag='984'><subfield code='b'>{normal}</subfield>"
            "<subfield code='c'>leaf</subfield></datafield>"
        )
    return (
        "<record>"
        "<controlfield tag='001'>guid-1</controlfield>"
        # 980 $a is the harvest flag; without it nothing is selected.
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield></datafield>"
        f"{fields}"
        "</record>"
    )


def _canonical(current: str | None = "215/B11/Box 3", normal: str | None = None):  # type: ignore[no-untyped-def]
    from adapters.steps.axiell_folio_sync.mapping import _extract_record, parse_xml

    return _extract_record(
        parse_xml(_record_with_locations(current, normal)), deleted=False
    )


def test_normal_location_is_read_from_984b() -> None:
    record = _canonical(normal="215/215;B11/215;B11;MR;84;3;7")

    assert record.normal_location == "215/215;B11/215;B11;MR;84;3;7"


def test_normal_location_is_none_when_984_is_absent() -> None:
    """The XSLT omits the field entirely for a record with no normal-location
    name, so absence is normal and must not be confused with an empty string."""
    record = _canonical(normal=None)

    assert record.normal_location is None


def test_the_two_locations_are_read_independently() -> None:
    """852 and 984 carry the same subfields; each must come from its own tag."""
    record = _canonical(current="CURRENT/PATH", normal="NORMAL/PATH")

    assert record.current_location == "CURRENT/PATH"
    assert record.normal_location == "NORMAL/PATH"


def test_the_shelf_location_comes_from_the_normal_location() -> None:
    """FOLIO's permanentLocation means where the item lives, so it is resolved from
    984 (normal), while the administrative note keeps the current location."""
    resolved: list[str | None] = []

    class RecordingRefCache(FakeRefCache):
        def resolve_location(self, name: str | None) -> str:
            resolved.append(name)
            return "loc-uuid"

    mapped = select_and_build(
        _record_with_locations(current="CURRENT/PATH", normal="NORMAL/PATH"),
        RecordingRefCache(),  # type: ignore[arg-type]
    )

    assert mapped is not None
    # Holdings and item both resolve, and only ever on the normal location — as
    # its leading code, which is the part a FOLIO location code can equal; the
    # whole "/"-separated path never resolves against the tenant.
    assert resolved == ["NORMAL", "NORMAL"]
    assert mapped.item.administrativeNotes == [
        f"{AXIELL_LOCATION_NOTE_PREFIX}: CURRENT/PATH"
    ]


def test_a_record_without_a_normal_location_fails() -> None:
    """No default: an item whose normal location is missing is reported, not
    shelved at a real but wrong location."""
    with pytest.raises(MappingError, match="Missing normal location"):
        select_and_build(
            _record_with_locations(current="CURRENT/PATH", normal=None),
            FakeRefCache(),  # type: ignore[arg-type]
        )


def test_a_normal_location_unknown_to_the_tenant_fails() -> None:
    """Same for a location the tenant has never heard of."""

    class EmptyRefCache(FakeRefCache):
        def resolve_location(self, name: str | None) -> str | None:
            return None

    with pytest.raises(MappingError, match="Unresolved normal location"):
        select_and_build(
            _record_with_locations(normal="NO/SUCH/PLACE"),
            EmptyRefCache(),  # type: ignore[arg-type]
        )


# ── loan type, from the use restriction (MARC 540 $a) ─────────────────────────
#
# Only the two terms whose material can be requested circulate. Everything else,
# including an absent 540$a and a term the table does not list, is "Unavailable".


class RecordingRefCache(FakeRefCache):
    """FakeRefCache that remembers the loan-type *name* it was asked to resolve."""

    def __init__(self) -> None:
        self.loan_type_names: list[str | None] = []

    def resolve_loan_type(self, name: str | None) -> str | None:
        self.loan_type_names.append(name)
        # Unknown to the tenant -> None, which is what makes `_resolve` raise.
        return "loan-uuid" if name in KNOWN_LOAN_TYPES else None


KNOWN_LOAN_TYPES = {"Can circulate", "Reading room", "Unavailable"}


def _record_with_access(
    category: str | None, use_restriction: str | None = None
) -> str:
    access = (
        f"<datafield tag='506'><subfield code='f'>{category}</subfield></datafield>"
        if category is not None
        else ""
    )
    if use_restriction is not None:
        access += (
            "<datafield tag='540'>"
            f"<subfield code='a'>{use_restriction}</subfield>"
            "</datafield>"
        )
    return (
        "<record>"
        "<controlfield tag='001'>guid-1</controlfield>"
        # 980 $a is the harvest flag; without it nothing is selected.
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield></datafield>"
        "<datafield tag='984'><subfield code='b'>215;HOME 1</subfield></datafield>"
        f"{access}"
        "</record>"
    )


def _loan_type_for(
    use_restriction: str | None, category: str | None = "OPEN"
) -> str | None:
    ref = RecordingRefCache()
    mapped = select_and_build(_record_with_access(category, use_restriction), ref)  # type: ignore[arg-type]
    assert mapped is not None
    return ref.loan_type_names[-1]


@pytest.mark.parametrize(
    ("use_restriction", "expected"),
    [
        ("Online request", "Can circulate"),
        ("Restricted", "Can circulate"),
        ("By appointment", "Unavailable"),
        ("Unavailable", "Unavailable"),
        ("By approval", "Unavailable"),
        ("Missing", "Unavailable"),
        ("Deaccessioned", "Unavailable"),
        ("Manual request", "Unavailable"),
    ],
)
def test_use_restriction_selects_the_loan_type(
    use_restriction: str, expected: str
) -> None:
    assert _loan_type_for(use_restriction) == expected


def test_every_table_term_is_tested() -> None:
    """A term added to the table without a row above would go untested."""
    assert len(USE_RESTRICTION_LOAN_TYPE) == 8


def test_absent_use_restriction_gives_unavailable() -> None:
    assert _loan_type_for(None) == "Unavailable"


def test_an_unlisted_use_restriction_gives_unavailable_rather_than_failing() -> None:
    """The vocabulary has grown before. A new term is treated as not
    requestable, and is never passed to the tenant as a loan type name."""
    assert _loan_type_for("Ask at the desk") == "Unavailable"


def test_use_restriction_matching_is_case_insensitive() -> None:
    assert _loan_type_for("ONLINE REQUEST") == "Can circulate"


def test_the_access_category_does_not_reach_the_loan_type() -> None:
    """The loan type reads 540$a only. A CLOSED item that AxC marks for online
    request still circulates, and its restriction shows in the item status."""
    assert _loan_type_for("Online request", category="CLOSED") == "Can circulate"
    assert _status_from(category="CLOSED") == "Restricted"


def test_unrecognised_access_category_still_raises() -> None:
    """Nothing maps "PRIVATE". The item status reads the category, so the record
    still fails rather than being guessed at."""
    with pytest.raises(MappingError, match="PRIVATE"):
        select_and_build(_record_with_access("PRIVATE"), RecordingRefCache())  # type: ignore[arg-type]


def _status_from(category: str | None) -> str:
    """The item status the mapping produces for one access category."""
    mapped = select_and_build(
        _record_with_access(category),
        RecordingRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None
    return mapped.item.status.name


# ── access category to item status, MARC 506 $f ───────────────────────────────
#
# The access category describes the item's state, which is what an item status is
# for. Before this mapping the status was the constant "Available", so the 9,819
# CLOSED records displayed as available regardless of their loan type.


@pytest.mark.parametrize(
    ("category", "expected"),
    [
        ("OPEN", "Available"),
        ("OPENWITHADVISORY", "Available"),
        # Available, not Restricted: restricted material can be requested
        # online, and the reader signs to accept the viewing conditions before
        # it is handed over.
        ("RESTRICTED", "Available"),
        ("PERMISSIONREQUIRED", "Restricted"),
        ("SAFEGUARDED", "Restricted"),
        ("CLOSED", "Restricted"),
        ("MISSING", "Missing"),
        ("DEACCESSIONED", "Withdrawn"),
        # Unknown, not Unavailable: the record's data is wrong, so the item's
        # real state has not been established.
        ("DATAISSUES", "Unknown"),
    ],
)
def test_access_category_selects_the_item_status(category: str, expected: str) -> None:
    assert _status_from(category=category) == expected


def test_absent_access_category_gives_an_unavailable_status() -> None:
    """An item of unknown access state is not presented as available."""
    assert _status_from(category=None) == "Unavailable"


def test_item_status_matching_is_case_insensitive() -> None:
    assert _status_from(category="Closed") == "Restricted"


# ── material type: required, with no default ──────────────────────────────────
#
# 655$a used to default to "book" when absent, which was wrong for every record
# it applied to: this is an archival corpus. A category that is present but
# unmapped already failed, because the raw AxC value resolves to nothing in the
# tenant, so removing the default makes the absent case behave the same way.


def _record_with_category(category: str | None) -> str:
    fields = (
        f"<datafield tag='655'><subfield code='a'>{category}</subfield></datafield>"
        if category is not None
        else ""
    )
    return (
        "<record>"
        "<controlfield tag='001'>guid-1</controlfield>"
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='984'><subfield code='b'>215;HOME 1</subfield></datafield>"
        f"{fields}"
        "</record>"
    )


def test_a_record_without_a_material_type_fails() -> None:
    """No default: 115 records in the corpus carry no 655$a, and they are now
    reported rather than typed `book`."""
    with pytest.raises(MappingError, match="Missing material type"):
        select_and_build(_record_with_category(None), FakeRefCache())  # type: ignore[arg-type]


def test_a_material_type_unknown_to_the_tenant_fails() -> None:
    """Unchanged by removing the default, and the reason removing it is safe: an
    unmapped category never reached the default anyway. `Visual Material` is
    real, on 4,347 records, and the tenant has no such material type."""

    class EmptyRefCache(FakeRefCache):
        def resolve_material_type(self, name: str | None) -> str | None:
            return None

    with pytest.raises(MappingError, match="Unresolved material type"):
        select_and_build(
            _record_with_category("Visual Material"),
            EmptyRefCache(),  # type: ignore[arg-type]
        )


def test_a_mapped_material_type_still_resolves() -> None:
    mapped = select_and_build(
        _record_with_category("Archives - Non-digital"),
        FakeRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None
    assert mapped.item.materialType.id == "mat-uuid"


@pytest.mark.parametrize(
    ("category", "expected"),
    [
        ("Archives - Non-digital", "archive"),
        ("Archives - Digital", "archive"),
        ("Archives - Hybrid", "archive"),
        ("Moving Image - Non-digital", "film"),
        ("Moving Image - Digital", "video format non-requestable"),
        ("Sound - Non-digital", "audio format requestable"),
        ("Sound - Digital", "audio format non-requestable"),
        # AxC says plain "Visual Material", never "Visual Material - Non-digital".
        ("Visual Material", "non-projected graphic"),
        ("Pictures", "non-projected graphic"),
    ],
)
def test_object_category_selects_the_material_type(
    category: str, expected: str
) -> None:
    """Every value that occurs in the corpus resolves, and to the agreed type."""
    asked: list[str | None] = []

    class RecordingRefCache(FakeRefCache):
        def resolve_material_type(self, name: str | None) -> str:
            asked.append(name)
            return "mat-uuid"

    mapped = select_and_build(
        _record_with_category(category),
        RecordingRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None
    assert asked == [expected]


def test_material_type_matching_is_case_insensitive() -> None:
    asked: list[str | None] = []

    class RecordingRefCache(FakeRefCache):
        def resolve_material_type(self, name: str | None) -> str:
            asked.append(name)
            return "mat-uuid"

    select_and_build(
        _record_with_category("ARCHIVES - NON-DIGITAL"),
        RecordingRefCache(),  # type: ignore[arg-type]
    )
    assert asked == ["archive"]


def test_the_retired_spaced_keys_are_gone() -> None:
    """The four "- Non Digital" (spaced) keys matched nothing in the corpus, so
    they were removed. Matching is not whitespace-insensitive, so a record using
    that spelling fails.

    It fails as Unmapped rather than Unresolved: strict_table rejects it at the
    table, before the resolver is consulted. Previously it only failed because
    no tenant happens to carry a material type called "Archives - Non Digital",
    which was incidental rather than enforced."""
    assert not [key for key in MATERIAL_TYPE if " - Non Digital" in key]

    with pytest.raises(MappingError, match="Unmapped material type"):
        select_and_build(
            _record_with_category("Archives - Non Digital"),
            FakeRefCache(),  # type: ignore[arg-type]
        )


# -- the material-type table is the agreed vocabulary ------------------------
#
# required=True only rejects an absent 655$a. On its own it does not make the
# table exhaustive: _resolve used to hand an unmapped value to the resolver
# unchanged, and RefCache.resolve_material_type accepts any name the tenant
# carries. So an AxC value of "archive", "computer media" or even "book" synced
# successfully without appearing in MATERIAL_TYPE at all, taking whatever
# requestability that FOLIO material type has. strict_table closes that.


class TenantRefCache(FakeRefCache):
    """Resolves only the material types the prod tenant actually carries.

    FakeRefCache resolves everything, which hides this class of bug: the point
    here is a value the tenant knows but the mapping table does not.
    """

    TENANT_MATERIAL_TYPES = {
        "archive",
        "audio format non-requestable",
        "audio format requestable",
        "book",
        "computer media",
        "film",
        "migration",
        "non-projected graphic",
        "serial",
        "video format non-requestable",
    }

    def resolve_material_type(self, name: str | None) -> str | None:
        return (
            "mat-uuid" if (name or "").lower() in self.TENANT_MATERIAL_TYPES else None
        )


@pytest.mark.parametrize(
    "category",
    [
        # Each of these is a real FOLIO material-type name on the tenant, so the
        # resolver would accept it. None is an AxC object_category.
        "archive",
        "ARCHIVE",
        "computer media",
        "migration",
        "serial",
        # The one that matters most: the default was removed so that nothing is
        # silently typed "book", and an AxC value of "book" must not reinstate it.
        "book",
    ],
)
def test_a_value_the_tenant_knows_but_the_table_does_not_is_rejected(
    category: str,
) -> None:
    with pytest.raises(MappingError, match="Unmapped material type"):
        select_and_build(
            _record_with_category(category),
            TenantRefCache(),  # type: ignore[arg-type]
        )


@pytest.mark.parametrize("category", sorted(MATERIAL_TYPE))
def test_every_table_entry_still_resolves(category: str) -> None:
    """The strict check must not reject the vocabulary itself."""
    mapped = select_and_build(
        _record_with_category(category),
        TenantRefCache(),  # type: ignore[arg-type]
    )
    assert mapped is not None
    assert mapped.item.materialType.id == "mat-uuid"


def test_the_three_failure_modes_are_distinguishable() -> None:
    """An operator reading the error needs to know which of these happened, as
    the fix differs: extend the table, fix the MARC, or provision the tenant."""
    with pytest.raises(MappingError, match="Missing material type"):
        select_and_build(_record_with_category(None), TenantRefCache())  # type: ignore[arg-type]

    with pytest.raises(MappingError, match="Unmapped material type"):
        select_and_build(_record_with_category("serial"), TenantRefCache())  # type: ignore[arg-type]

    # In the table, but the tenant does not carry the name it maps to.
    class NoMaterialTypes(FakeRefCache):
        def resolve_material_type(self, name: str | None) -> str | None:
            return None

    with pytest.raises(MappingError, match="Unresolved material type"):
        select_and_build(
            _record_with_category("Archives - Non-digital"),
            NoMaterialTypes(),  # type: ignore[arg-type]
        )
