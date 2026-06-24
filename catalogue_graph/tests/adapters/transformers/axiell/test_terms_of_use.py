"""Tests for extract_terms_of_use, ported from the Scala CalmTermsOfUseTest."""

# mypy: allow-untyped-calls

from unittest.mock import patch

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.terms_of_use import extract_terms_of_use
from models.pipeline.access_status import ByAppointment, Closed, PermissionRequired, Restricted
from models.pipeline.note import Note


def _make_record(
    status: str | None = None,
    conditions: str | None = None,
    closed_until: str | None = None,
    restricted_until: str | None = None,
) -> Record:
    """Build a minimal MARC record with access fields populated.

    status         → 506 $f  (Axiell access status code, e.g. "OPEN", "RESTRICTED")
    conditions     → 506 $a  (access conditions text)
    closed_until   → 506 $g  (date in d/M/yyyy format)
    restricted_until → 540 $g (date in d/M/yyyy format)
    """
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


def _contents(note: Note | None) -> str | None:
    return note.contents if note else None


# ---------------------------------------------------------------------------
# Tests that don't need Closed status (Axiell-native statuses only)
# ---------------------------------------------------------------------------


def test_open_with_conditions_no_dates() -> None:
    """Open item: conditions are returned as-is."""
    record = _make_record(
        status="OPEN",
        conditions="The papers are available subject to the usual conditions of access to Archives and Manuscripts material.",
    )
    assert _contents(extract_terms_of_use(record)) == (
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material."
    )


def test_restricted_with_conditions_no_dates() -> None:
    """Restricted item with no date: conditions are returned as-is."""
    record = _make_record(
        status="RESTRICTED",
        conditions=(
            "Digital records cannot be ordered or viewed online. "
            "Requests to view digital records onsite are considered on a case by case basis. "
            "Please contact collections@wellcome.ac.uk for more details."
        ),
    )
    assert _contents(extract_terms_of_use(record)) == (
        "Digital records cannot be ordered or viewed online. "
        "Requests to view digital records onsite are considered on a case by case basis. "
        "Please contact collections@wellcome.ac.uk for more details."
    )


def test_restricted_date_already_in_conditions() -> None:
    """Restricted item: date already present in conditions → no repetition."""
    record = _make_record(
        status="RESTRICTED",
        conditions=(
            "This file is restricted until 01/01/2039 for data protection reasons. "
            "Readers must complete and sign a Restricted Access undertaking form to apply for access."
        ),
        restricted_until="01/01/2039",
    )
    assert _contents(extract_terms_of_use(record)) == (
        "This file is restricted until 01/01/2039 for data protection reasons. "
        "Readers must complete and sign a Restricted Access undertaking form to apply for access."
    )


def test_restricted_date_not_in_conditions() -> None:
    """Restricted item: date not in conditions → append 'Restricted until …'."""
    record = _make_record(
        status="RESTRICTED",
        conditions=(
            "This file is restricted for data protection reasons. "
            "When a reader arrives onsite, they will be required to sign a Restricted Access form "
            "agreeing to anonymise personal data before viewing the file."
        ),
        restricted_until="01/01/2060",
    )
    assert _contents(extract_terms_of_use(record)) == (
        "This file is restricted for data protection reasons. "
        "When a reader arrives onsite, they will be required to sign a Restricted Access form "
        "agreeing to anonymise personal data before viewing the file. "
        "Restricted until 1 January 2060."
    )


def test_just_a_status_no_conditions_no_dates() -> None:
    """Item with only an access status: nothing useful to output."""
    record = _make_record(status="OPEN")
    assert extract_terms_of_use(record) is None


def test_no_access_information() -> None:
    """Item with no access fields at all: returns None."""
    assert extract_terms_of_use(_make_record()) is None


def test_permission_required_with_restrictions_date_not_in_conditions() -> None:
    """PermissionRequired + conditions mentioning both permission and restrictions,
    date not yet in conditions → append 'Restricted until …'."""
    record = _make_record(
        status="PERMISSIONREQUIRED",
        conditions=(
            'Permission must be obtained from <a href="mailto:barbie.antonis@gmail.com">the Winnicott Trust</a>, '
            "and the usual conditions of access to Archives and Manuscripts material apply; "
            "a Reader's Undertaking must be completed. "
            "In addition there are Data Protection restrictions on this item and an additional "
            "application for access must be completed."
        ),
        restricted_until="01/01/2072",
    )
    assert _contents(extract_terms_of_use(record)) == (
        'Permission must be obtained from <a href="mailto:barbie.antonis@gmail.com">the Winnicott Trust</a>, '
        "and the usual conditions of access to Archives and Manuscripts material apply; "
        "a Reader's Undertaking must be completed. "
        "In addition there are Data Protection restrictions on this item and an additional "
        "application for access must be completed. "
        "Restricted until 1 January 2072."
    )


def test_removes_trailing_whitespace() -> None:
    """Trailing whitespace on conditions is stripped; date already in conditions."""
    record = _make_record(
        status="RESTRICTED",
        conditions=(
            "This file is restricted until 01/01/2024 for data protection reasons. "
            "Readers must complete and sign a Restricted Access undertaking form to apply for access.\n\n"
        ),
        restricted_until="01/01/2024",
    )
    assert _contents(extract_terms_of_use(record)) == (
        "This file is restricted until 01/01/2024 for data protection reasons. "
        "Readers must complete and sign a Restricted Access undertaking form to apply for access."
    )


def test_fallback_case() -> None:
    """By Appointment + conditions + restrictedUntil → catch-all appends 'Restricted until …'."""
    record = _make_record(
        status="BYAPPOINTMENT",
        conditions=(
            "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. "
            "In addition a Restricted Access form must be completed to apply for access to this file."
        ),
        restricted_until="01/01/2066",
    )
    assert _contents(extract_terms_of_use(record)) == (
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. "
        "In addition a Restricted Access form must be completed to apply for access to this file. "
        "Restricted until 1 January 2066."
    )


# ---------------------------------------------------------------------------
# Tests that require Closed access status (mocked, as Axiell has no CLOSED
# MARC value in the current data)
# ---------------------------------------------------------------------------

_PATCH_TARGET = "adapters.transformers.axiell.terms_of_use.extract_access_status"


def test_closed_with_conditions_no_dates() -> None:
    """Closed item with conditions, no date: conditions are returned as-is."""
    record = _make_record(conditions="Closed on depositor agreement.")
    with patch(_PATCH_TARGET, return_value=Closed):
        assert _contents(extract_terms_of_use(record)) == "Closed on depositor agreement."


def test_closed_date_already_in_conditions() -> None:
    """Closed item: date already present in conditions (with ordinal) → no repetition."""
    record = _make_record(
        conditions="Closed under the Data Protection Act until 1st January 2039.",
        closed_until="01/01/2039",
    )
    with patch(_PATCH_TARGET, return_value=Closed):
        assert _contents(extract_terms_of_use(record)) == (
            "Closed under the Data Protection Act until 1st January 2039."
        )


def test_closed_date_not_in_conditions() -> None:
    """Closed item: date not in conditions → append 'Closed until …'."""
    record = _make_record(
        conditions="Closed under the Data Protection Act.",
        closed_until="01/01/2039",
    )
    with patch(_PATCH_TARGET, return_value=Closed):
        assert _contents(extract_terms_of_use(record)) == (
            "Closed under the Data Protection Act. Closed until 1 January 2039."
        )


def test_closed_no_conditions() -> None:
    """Closed item with no conditions: synthesise 'Closed until …' note."""
    record = _make_record(closed_until="01/01/2068")
    with patch(_PATCH_TARGET, return_value=Closed):
        assert _contents(extract_terms_of_use(record)) == "Closed until 1 January 2068."


def test_adds_missing_full_stop() -> None:
    """Conditions lacking a trailing period get one before appending the date."""
    record = _make_record(
        conditions="This file is closed for data protection reasons and cannot be accessed",
        closed_until="01/01/2055",
    )
    with patch(_PATCH_TARGET, return_value=Closed):
        assert _contents(extract_terms_of_use(record)) == (
            "This file is closed for data protection reasons and cannot be accessed. "
            "Closed until 1 January 2055."
        )


def test_returns_none_for_no_useful_access_info() -> None:
    """Duplicate of the 'no access information' case — asserts Note is absent."""
    assert extract_terms_of_use(_make_record()) is None
