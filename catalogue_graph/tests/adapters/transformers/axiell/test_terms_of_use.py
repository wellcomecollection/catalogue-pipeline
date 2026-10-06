"""Tests for extract_terms_of_use, ported from the Scala CalmTermsOfUseTest."""

# mypy: allow-untyped-calls

import pytest
from freezegun import freeze_time
from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.terms_of_use import extract_terms_of_use


def _make_record(
    status: str | None = None,
    conditions: str | None = None,
    until: str | None = None,
) -> Record:
    """Build a minimal MARC record with access fields populated.

    Axiell holds the restricted-until and closed-until date in the same 506 $g
    subfield; the access status in $f says which of the two it is. Dates must be
    in yyyy-mm-dd format, matching what the transformer parses.
    """
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))

    subfields_506: list[Subfield] = []
    if status:
        subfields_506.append(Subfield(code="f", value=status))
    if conditions:
        subfields_506.append(Subfield(code="a", value=conditions))
    if until:
        subfields_506.append(Subfield(code="g", value=until))
    if subfields_506:
        record.add_field(Field(tag="506", subfields=subfields_506))

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
        until="2039-01-01",
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
        until="2060-01-01",
    )
    assert (
        extract_terms_of_use(record) == f"{conditions}Restricted until 1 January 2060."
    )


@pytest.mark.parametrize("status", [None, "OPEN", "RESTRICTED", "CLOSED"])
def test_status_alone_produces_no_note(status: str | None) -> None:
    """A status on its own never synthesises a note, and that holds for Closed
    and Restricted too: those only produce one when 506 $g supplies a date for
    the status to label."""
    assert extract_terms_of_use(_make_record(status=status)) is None


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
        until="2072-01-01",
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
        until="2054-01-01",
    )
    assert extract_terms_of_use(record) == conditions.strip()


@freeze_time("2025-01-01T12:00:00Z")
def test_date_with_no_status_to_label_it_is_dropped() -> None:
    """A 506 $g date only becomes a sentence when the status says the item is
    closed or restricted. By Appointment says neither, so the date is dropped
    rather than asserting an access status the record does not give."""
    conditions = (
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. "
        "In addition a Restricted Access form must be completed to apply for access to this file."
    )
    record = _make_record(
        status="BYAPPOINTMENT",
        conditions=conditions,
        until="2066-01-01",
    )
    assert extract_terms_of_use(record) == conditions


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_date_already_in_conditions() -> None:
    """Closed item, date already present in conditions (with ordinal): no repetition."""
    conditions = "Closed under the Data Protection Act until 1st January 2039."
    record = _make_record(status="CLOSED", conditions=conditions, until="2039-01-01")
    assert extract_terms_of_use(record) == conditions


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_date_not_in_conditions() -> None:
    """Closed item, date not in conditions: append 'Closed until <date>'."""
    record = _make_record(
        status="CLOSED",
        conditions="Closed under the Data Protection Act.",
        until="2039-01-01",
    )
    assert extract_terms_of_use(record) == (
        "Closed under the Data Protection Act. Closed until 1 January 2039."
    )


@freeze_time("2025-01-01T12:00:00Z")
def test_closed_no_conditions() -> None:
    """Closed item with no conditions: synthesise 'Closed until <date>' note."""
    record = _make_record(status="CLOSED", until="2068-01-01")
    assert extract_terms_of_use(record) == "Closed until 1 January 2068."


@freeze_time("2025-01-01T12:00:00Z")
def test_adds_missing_full_stop() -> None:
    """Conditions lacking a trailing period get one before appending the date."""
    record = _make_record(
        status="CLOSED",
        conditions="This file is closed for data protection reasons and cannot be accessed",
        until="2055-01-01",
    )
    assert extract_terms_of_use(record) == (
        "This file is closed for data protection reasons and cannot be accessed. "
        "Closed until 1 January 2055."
    )


def test_returns_none_for_whitespace_only_conditions() -> None:
    record = _make_record(status="OPEN", conditions="  \n\t")
    assert extract_terms_of_use(record) is None
