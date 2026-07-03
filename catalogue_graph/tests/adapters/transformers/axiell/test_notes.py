"""Tests for extract_notes (arrangement-note branch)."""

# mypy: allow-untyped-calls

from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.axiell.notes import extract_notes
from adapters.transformers.marc.notes import (
    ARRANGEMENT_NOTE,
    LANGUAGE_NOTE,
    TERMS_OF_USE,
)


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


def _field(tag: str, subfield_code: str, value: str) -> Field:
    return Field(
        tag=tag,
        indicators=Indicators(" ", " "),
        subfields=[Subfield(code=subfield_code, value=value)],
    )


def test_546_parsed_as_languages_not_language_note() -> None:
    """A parseable 546 $a value is extracted as Language objects, not a LANGUAGE_NOTE.

    The base MARC logic would emit a LANGUAGE_NOTE. The Axiell path instead
    hands the field to extract_languages, which resolves recognised language
    names and discards the raw text.
    """
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    record.add_field(_field("546", "a", "French and English"))
    notes = extract_notes(record)
    assert not any(n.note_type == LANGUAGE_NOTE for n in notes)


def test_506_terms_of_use_normalised_by_axiell_logic() -> None:
    """A 506 $a value is normalised (period added) by the Axiell access-conditions logic.

    The base MARC logic would emit a TERMS_OF_USE note with the raw text.
    The Axiell path calls extract_terms_of_use which ensures the text ends
    with a full stop.
    """
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    record.add_field(_field("506", "a", "Access restricted to staff"))
    notes = extract_notes(record)
    terms_notes = [n for n in notes if n.note_type == TERMS_OF_USE]
    assert len(terms_notes) == 1
    assert terms_notes[0].contents == "Access restricted to staff."


def test_540_text_content_not_emitted_as_terms_of_use() -> None:
    """540 $a text is ignored by the Axiell terms-of-use logic.

    The Axiell path reads 540 $g (a restricted-until date), not $a. A record
    with only a 540 $a value therefore produces no TERMS_OF_USE note, whereas
    the base MARC logic would have emitted one verbatim.
    """
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    record.add_field(_field("540", "a", "Reproductions may be made for personal use."))
    notes = extract_notes(record)
    assert not any(n.note_type == TERMS_OF_USE for n in notes)
