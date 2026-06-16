from pymarc.record import Field, Indicators, Subfield

from adapters.transformers.marc.notes import (
    _NOTES_FIELDS,
    AWARDS_NOTE,
    BIBLIOGRAPHICAL_INFORMATION,
    BINDING_INFORMATION,
    BIOGRAPHICAL_NOTE,
    CITE_AS_NOTE,
    CONTENTS_NOTE,
    COPYRIGHT_NOTE,
    CREDITS_NOTE,
    DISSERTATION_NOTE,
    EXHIBITIONS_NOTE,
    FUNDING_INFORMATION,
    GENERAL_NOTE,
    LANGUAGE_NOTE,
    LETTERING_NOTE,
    LOCATION_OF_DUPLICATES_NOTE,
    LOCATION_OF_ORIGINAL_NOTE,
    OWNERSHIP_NOTE,
    PUBLICATIONS_NOTE,
    REFERENCES_NOTE,
    RELATED_MATERIAL,
    REPRODUCTION_NOTE,
    TERMS_OF_USE,
    TIME_AND_PLACE_NOTE,
    extract_notes,
)
from models.pipeline.note import Note
from tests.adapters.transformers.conftest import _record_with_fields


def _simple_field(tag: str, content: str, indicator1: str = " ") -> Field:
    return Field(
        tag=tag,
        indicators=Indicators(indicator1, " "),
        subfields=[Subfield(code="a", value=content)],
    )


def test_extracts_notes_from_all_fields() -> None:
    cases = [
        ("500", "general note a", GENERAL_NOTE),
        ("501", "general note b", GENERAL_NOTE),
        ("502", "dissertation note", DISSERTATION_NOTE),
        ("504", "bib info a", BIBLIOGRAPHICAL_INFORMATION),
        ("505", "contents note", CONTENTS_NOTE),
        ("506", "typical terms of use", TERMS_OF_USE),
        ("508", "credits note a", CREDITS_NOTE),
        ("510", "references a", REFERENCES_NOTE),
        ("511", "credits note b", CREDITS_NOTE),
        ("514", "Completeness:", LETTERING_NOTE),
        ("518", "time and place note", TIME_AND_PLACE_NOTE),
        ("533", "reproduction a", REPRODUCTION_NOTE),
        ("534", "reproduction b", REPRODUCTION_NOTE),
        ("535", "location of original note", LOCATION_OF_ORIGINAL_NOTE),
        ("536", "funding information", FUNDING_INFORMATION),
        ("540", "terms of use", TERMS_OF_USE),
        ("542", "copyright a", COPYRIGHT_NOTE),
        ("544", "related material a", RELATED_MATERIAL),
        ("545", "bib info b", BIOGRAPHICAL_NOTE),
        ("546", "Marriage certificate: German; Fraktur.", LANGUAGE_NOTE),
        ("547", "general note c", GENERAL_NOTE),
        ("562", "general note d", GENERAL_NOTE),
        ("563", "binding info note", BINDING_INFORMATION),
        ("581", "publications b", PUBLICATIONS_NOTE),
        ("585", "exhibitions", EXHIBITIONS_NOTE),
        ("586", "awards", AWARDS_NOTE),
    ]
    fields = [_simple_field(tag, content) for tag, content, _ in cases]
    record = _record_with_fields(*fields)
    expected = [Note(contents=content, note_type=nt) for _, content, nt in cases]
    assert extract_notes(record) == expected


def test_extracts_all_notes_when_duplicate_fields() -> None:
    record = _record_with_fields(
        _simple_field("500", "note a"),
        _simple_field("500", "note b"),
    )
    assert extract_notes(record) == [
        Note(contents="note a", note_type=GENERAL_NOTE),
        Note(contents="note b", note_type=GENERAL_NOTE),
    ]


def test_does_not_extract_notes_from_non_note_fields() -> None:
    record = _record_with_fields(
        _simple_field("100", "not a note"),
        _simple_field("530", "not a note"),
    )
    assert extract_notes(record) == []


def test_preserves_html_in_notes_fields() -> None:
    record = _record_with_fields(_simple_field("500", "<p>note</p>"))
    assert extract_notes(record) == [
        Note(contents="<p>note</p>", note_type=GENERAL_NOTE)
    ]


def test_concatenates_subfields_on_single_field_into_single_note() -> None:
    field = Field(
        tag="500",
        indicators=Indicators(" ", " "),
        subfields=[
            Subfield(code="a", value="1st part."),
            Subfield(code="b", value="2nd part."),
            Subfield(code="c", value="3rd part."),
        ],
    )
    record = _record_with_fields(field)
    assert extract_notes(record) == [
        Note(contents="1st part. 2nd part. 3rd part.", note_type=GENERAL_NOTE)
    ]


def test_does_not_concatenate_separate_fields() -> None:
    record = _record_with_fields(
        _simple_field("500", "1st note."),
        _simple_field("500", "2nd note."),
    )
    assert extract_notes(record) == [
        Note(contents="1st note.", note_type=GENERAL_NOTE),
        Note(contents="2nd note.", note_type=GENERAL_NOTE),
    ]


def test_distinguishes_based_on_first_indicator_of_535() -> None:
    record = _record_with_fields(
        _simple_field("535", "The originals are in Oman", indicator1="1"),
        _simple_field("535", "The duplicates are in Denmark", indicator1="2"),
    )
    assert extract_notes(record) == [
        Note(
            contents="The originals are in Oman",
            note_type=LOCATION_OF_ORIGINAL_NOTE,
        ),
        Note(
            contents="The duplicates are in Denmark",
            note_type=LOCATION_OF_DUPLICATES_NOTE,
        ),
    ]


def test_only_gets_ownership_note_if_561_first_indicator_is_1() -> None:
    record = _record_with_fields(
        _simple_field(
            "561", "Provenance: one plate in the set of plates", indicator1="1"
        ),
        _simple_field("561", "Purchased from John Smith on 01/01/2001", indicator1="0"),
        _simple_field("561", "Private contact details for John Smith"),
    )
    assert extract_notes(record) == [
        Note(
            contents="Provenance: one plate in the set of plates",
            note_type=OWNERSHIP_NOTE,
        )
    ]


def test_suppresses_subfield_5_universally() -> None:
    fields_with_5 = [
        Field(
            tag=tag,
            indicators=Indicators("1", " "),
            subfields=[
                Subfield(code="a", value="Main bit."),
                Subfield(code="5", value="UkLW"),
            ],
        )
        for tag in _NOTES_FIELDS
    ]
    fields_without_5 = [
        Field(
            tag=tag,
            indicators=Indicators("1", " "),
            subfields=[Subfield(code="a", value="Main bit.")],
        )
        for tag in _NOTES_FIELDS
    ]
    record_with_5 = _record_with_fields(*fields_with_5)
    record_without_5 = _record_with_fields(*fields_without_5)
    notes_with = sorted(
        (n.contents, n.note_type.id) for n in extract_notes(record_with_5)
    )
    notes_without = sorted(
        (n.contents, n.note_type.id) for n in extract_notes(record_without_5)
    )
    assert notes_with == notes_without


def test_skips_notes_which_are_just_whitespace() -> None:
    fields = [_simple_field("535", content) for content in ["\u00a0", "", "\t\n"]]
    record = _record_with_fields(*fields)
    assert extract_notes(record) == []


def test_creates_clickable_link_for_subfield_u() -> None:
    field = Field(
        tag="540",
        indicators=Indicators(" ", " "),
        subfields=[
            Subfield(
                code="a",
                value="The National Library of Medicine believes this item to be in the public domain.",
            ),
            Subfield(
                code="u",
                value="https://creativecommons.org/publicdomain/mark/1.0/",
            ),
            Subfield(code="5", value="DNLM"),
        ],
    )
    record = _record_with_fields(field)
    notes = extract_notes(record)
    assert len(notes) == 1
    assert notes[0].contents == (
        "The National Library of Medicine believes this item to be in the public domain."
        ' <a href="https://creativecommons.org/publicdomain/mark/1.0/">'
        "https://creativecommons.org/publicdomain/mark/1.0/</a>"
    )


def test_does_not_create_link_if_subfield_u_is_not_a_url() -> None:
    field = Field(
        tag="540",
        indicators=Indicators(" ", " "),
        subfields=[
            Subfield(
                code="a",
                value="The National Library of Medicine believes this item to be in the public domain.",
            ),
            Subfield(code="u", value="CC-0 license"),
        ],
    )
    record = _record_with_fields(field)
    notes = extract_notes(record)
    assert len(notes) == 1
    assert notes[0].contents == (
        "The National Library of Medicine believes this item to be in the public domain."
        " CC-0 license"
    )


def test_strips_whitespace_from_subfield_u() -> None:
    field = Field(
        tag="540",
        indicators=Indicators(" ", " "),
        subfields=[
            Subfield(code="u", value="   https://example.com/works/a65fex5m   "),
        ],
    )
    record = _record_with_fields(field)
    notes = extract_notes(record)
    assert len(notes) == 1
    assert notes[0].contents == (
        '<a href="https://example.com/works/a65fex5m">https://example.com/works/a65fex5m</a>'
    )
