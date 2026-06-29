"""Tests for extract_notes (arrangement-note branch)."""

# mypy: allow-untyped-calls

from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.notes import extract_notes
from adapters.transformers.marc.notes import ARRANGEMENT_NOTE


def _make_record(arrangement: str | None = None) -> Record:
    """Build a minimal MARC record with an optional 351 $b arrangement value."""
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    if arrangement is not None:
        record.add_field(
            Field(tag="351", subfields=[Subfield(code="b", value=arrangement)])
        )
    return record


def test_arrangement_note_present() -> None:
    """A 351 $b value is returned as an arrangement note."""
    record = _make_record(arrangement="Arranged alphabetically by correspondent.")
    notes = extract_notes(record)
    arrangement_notes = [n for n in notes if n.note_type == ARRANGEMENT_NOTE]
    assert len(arrangement_notes) == 1
    assert arrangement_notes[0].contents == "Arranged alphabetically by correspondent."


def test_arrangement_note_absent() -> None:
    """No 351 $b: no arrangement note is added."""
    record = _make_record()
    notes = extract_notes(record)
    assert not any(n.note_type == ARRANGEMENT_NOTE for n in notes)


def test_arrangement_note_empty_string_not_added() -> None:
    """An empty 351 $b should not produce an arrangement note."""
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    record.add_field(Field(tag="351", subfields=[Subfield(code="b", value="")]))
    notes = extract_notes(record)
    assert not any(n.note_type == ARRANGEMENT_NOTE for n in notes)
