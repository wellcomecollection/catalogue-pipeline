"""Tests for extract_access_status."""

# mypy: allow-untyped-calls

from freezegun import freeze_time
from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.access_status import extract_access_status
from models.pipeline.access_status import Closed, Open


def _make_record(
    status: str | None = None,
    closed_until: str | None = None,
) -> Record:
    """Build a minimal MARC record with optional 506 $f status and 506 $g closed_until date."""
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))

    subfields_506: list[Subfield] = []
    if status:
        subfields_506.append(Subfield(code="f", value=status))
    if closed_until:
        subfields_506.append(Subfield(code="g", value=closed_until))
    if subfields_506:
        record.add_field(Field(tag="506", subfields=subfields_506))

    return record


@freeze_time("2026-06-25")
def test_closed_until_in_the_future_returns_closed() -> None:
    """No status value, but closed_until is in the future: item should be Closed."""
    record = _make_record(closed_until="2030-01-01")
    assert extract_access_status(record) == Closed


@freeze_time("2026-06-25")
def test_closed_until_in_the_past_does_not_return_closed() -> None:
    """closed_until is in the past: the closed-until branch is skipped."""
    record = _make_record(closed_until="2020-01-01")
    assert extract_access_status(record) != Closed


@freeze_time("2026-06-25")
def test_known_status_takes_precedence_over_closed_until() -> None:
    """A recognised status value (OPEN) should be returned even when closed_until is in the future."""
    record = _make_record(status="OPEN", closed_until="2030-01-01")
    assert extract_access_status(record) == Open


@freeze_time("2026-06-25")
def test_no_status_no_closed_until_returns_none() -> None:
    """No status and no closed_until date: returns None."""
    record = _make_record()
    assert extract_access_status(record) is None
