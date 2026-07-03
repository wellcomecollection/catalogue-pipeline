"""Tests for extract_terms_of_use, ported from the Scala CalmTermsOfUseTest."""

# mypy: allow-untyped-calls

from freezegun import freeze_time
from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.terms_of_use import extract_terms_of_use


def _make_record(
    status: str | None = None,
    conditions: str | None = None,
    closed_until: str | None = None,
    restricted_until: str | None = None,
) -> Record:
    """Build a minimal MARC record with access fields populated. Dates must be in yyyy/M/d format."""
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))

    subfields_506: list[Subfield] = []
    if status:
        subfields_506.append(Subfield(code="f", value=status))
    if conditions:
        subfields_506.append(Subfield(code="a", value=conditions))
    if closed_until:
        subfields_506.append(Subfield(code="g", value=closed_until))
    if subfields_506:
        record.add_field(Field(tag="506", subfields=subfields_506))

    if restricted_until:
        record.add_field(
            Field(tag="540", subfields=[Subfield(code="g", value=restricted_until)])
        )

    return record


def test_open_with_conditions_no_dates() -> None:
    """Open item: conditions are returned as-is."""
    conditions = "The papers are available subject to the usual conditions of access to Archives and Manuscripts material."
    record = _make_record(status="OPEN", conditions=conditions)
    assert extract_terms_of_use(record) == conditions


def test_restricted_with_conditions_no_dates() -> None:
    """Restricted item with no date: conditions are returned as-is."""
    conditions = (
        "Digital records cannot be ordered or viewed online. "
        "Requests to view digital records onsite are considered on a case by case basis. "
        "Please contact collections@wellcome.ac.uk for more details."
    )

    record = _make_record(status="RESTRICTED", conditions=conditions)
    assert extract_terms_of_use(record) == conditions


@freeze_time("2025-01-01T12:00:00Z")
def test_restricted_date_already_in_conditions() -> None:
    """Restricted item, date already present in conditions: no repetition."""
    conditions = (
        "This file is restricted until 01/01/2039 for data protection reasons. "
        "Readers must complete and sign a Restricted Access undertaking form to apply for access."
    )
    record = _make_record(
        status="RESTRICTED",
        conditions=conditions,
        restricted_until="01/01/2039",
    )
    assert extract_terms_of_use(record) == conditions


@freeze_time("2025-01-01T12:00:00Z")
def test_restricted_date_not_in_conditions() -> None:
    """Restricted item, date not in conditions: append 'Restricted until <date>'."""
    conditions = (
        "This file is restricted for data protection reasons. "
        "When a reader arrives onsite, they will be required to sign a Restricted Access form "
        "agreeing to anonymise personal data before viewing the file. "
    )
    record = _make_record(
        status="RESTRICTED",
        conditions=conditions,
        restricted_until="2060-01-01",
    )
    assert (
        extract_terms_of_use(record) == f"{conditions}Restricted until 1 January 2060."
    )


def test_just_a_status_no_conditions_no_dates() -> None:
    """Item with only an access status: nothing useful to output."""
    record = _make_record(status="OPEN")
    assert extract_terms_of_use(record) is None


def test_no_access_information() -> None:
    """Item with no access fields at all: returns None."""
    assert extract_terms_of_use(_make_record()) is None


@freeze_time("2025-01-01T12:00:00Z")
def test_permission_required_with_restrictions_date_not_in_conditions() -> None:
    """PermissionRequired + conditions mentioning both permission and restrictions,
    date not yet in conditions: append 'Restricted until <date>'."""
    conditions = (
        'Permission must be obtained from <a href="mailto:barbie.antonis@gmail.com">the Winnicott Trust</a>, '
        "and the usual conditions of access to Archives and Manuscripts material apply; "
        "a Reader's Undertaking must be completed. "
        "In addition there are Data Protection restrictions on this item and an additional "
        "application for access must be completed."
    )
    record = _make_record(
        status="PERMISSIONREQUIRED",
        conditions=conditions,
        restricted_until="2072-01-01",
    )
    assert (
        extract_terms_of_use(record) == f"{conditions} Restricted until 1 January 2072."
    )


@freeze_time("2025-01-01T12:00:00Z")
def test_removes_trailing_whitespace() -> None:
    """Trailing whitespace on conditions is stripped, date already in conditions."""
    conditions = (
        "This file is restricted until 01/01/2054 for data protection reasons. "
        "Readers must complete and sign a Restricted Access undertaking form to apply for access.\n\n"
    )
    record = _make_record(
        status="RESTRICTED",
        conditions=conditions,
        restricted_until="2054-01-01",
    )
    assert extract_terms_of_use(record) == conditions.strip()


@freeze_time("2025-01-01T12:00:00Z")
def test_fallback_case() -> None:
    """By Appointment + conditions + restricted until date: catch-all appends 'Restricted until <date>'."""
    conditions = (
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. "
        "In addition a Restricted Access form must be completed to apply for access to this file."
    )
    record = _make_record(
        status="BYAPPOINTMENT",
        conditions=conditions,
        restricted_until="2066-01-01",
    )
    assert (
        extract_terms_of_use(record) == f"{conditions} Restricted until 1 January 2066."
    )


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_date_already_in_conditions() -> None:
    """Closed item, date already present in conditions (with ordinal): no repetition."""
    conditions = "Closed under the Data Protection Act until 1st January 2039."
    record = _make_record(conditions=conditions, closed_until="2039-01-01")
    assert extract_terms_of_use(record) == conditions


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_date_not_in_conditions() -> None:
    """Closed item, date not in conditions: append 'Closed until <date>'."""
    record = _make_record(
        conditions="Closed under the Data Protection Act.",
        closed_until="2039-01-01",
    )
    assert extract_terms_of_use(record) == (
        "Closed under the Data Protection Act. Closed until 1 January 2039."
    )


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_no_conditions() -> None:
    """Closed item with no conditions: synthesise 'Closed until <date>' note."""
    record = _make_record(closed_until="2068-01-01")
    assert extract_terms_of_use(record) == "Closed until 1 January 2068."


@freeze_time("2025-01-01T12:00:00Z")
def test_adds_missing_full_stop() -> None:
    """Conditions lacking a trailing period get one before appending the date."""
    record = _make_record(
        conditions="This file is closed for data protection reasons and cannot be accessed",
        closed_until="2055-01-01",
    )
    assert extract_terms_of_use(record) == (
        "This file is closed for data protection reasons and cannot be accessed. "
        "Closed until 1 January 2055."
    )


def test_returns_none_for_whitespace_only_conditions() -> None:
    record = _make_record(status="OPEN", conditions="  \n\t")
    assert extract_terms_of_use(record) is None
