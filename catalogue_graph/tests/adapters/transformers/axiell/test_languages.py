# mypy: allow-untyped-calls

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.languages import extract_languages
from adapters.transformers.marc.notes import LANGUAGE_NOTE
from models.pipeline.id_label import Language
from models.pipeline.note import Note


def _make_record(*language_values: str) -> Record:
    """Build a minimal MARC record with 546 $a language fields."""
    record = Record()
    record.add_field(Field(tag="001", data="test_id"))
    for value in language_values:
        record.add_field(Field(tag="546", subfields=[Subfield(code="a", value=value)]))
    return record


def _language_note(contents: str) -> Note:
    return Note(contents=contents, note_type=LANGUAGE_NOTE)


# --- Degenerate cases --------------------------------------------------------


@pytest.mark.parametrize("value", ["", "  ", "\n\n"])
def test_degenerate_cases(value: str) -> None:
    assert extract_languages(_make_record(value)) == ([], [])


# --- Exact matches -----------------------------------------------------------


@pytest.mark.parametrize(
    "lang_field, expected_languages",
    [
        ("English", [Language(label="English", id="eng")]),
        ("Swedish", [Language(label="Swedish", id="swe")]),
        # A variant name in the MARC Language list
        ("Mandarin", [Language(label="Mandarin", id="chi")]),
        ("Middle English", [Language(label="Middle English", id="enm")]),
    ],
)
def test_exact_matches(lang_field: str, expected_languages: list[Language]) -> None:
    languages, notes = extract_languages(_make_record(lang_field))
    assert languages == expected_languages
    assert notes == []


# --- Multiple matches ---------------------------------------------------------


@pytest.mark.parametrize(
    "lang_field, expected_languages",
    [
        (
            "Portuguese\nSpanish",
            [
                Language(label="Portuguese", id="por"),
                Language(label="Spanish", id="spa"),
            ],
        ),
        ("English.", [Language(label="English", id="eng")]),
        ("English`", [Language(label="English", id="eng")]),
        (
            "German; French",
            [Language(label="German", id="ger"), Language(label="French", id="fre")],
        ),
        (
            "English, Chinese",
            [Language(label="English", id="eng"), Language(label="Chinese", id="chi")],
        ),
        (
            "German, French, ",
            [Language(label="German", id="ger"), Language(label="French", id="fre")],
        ),
        (
            "English/French",
            [Language(label="English", id="eng"), Language(label="French", id="fre")],
        ),
        # We have to be a little bit careful splitting 'and' -- some languages
        # have "and" as part of the name, and we don't want to lose those.
        (
            "English/Ganda",
            [Language(label="English", id="eng"), Language(label="Ganda", id="lug")],
        ),
        (
            "English and Russian",
            [Language(label="English", id="eng"), Language(label="Russian", id="rus")],
        ),
    ],
)
def test_multiple_matches(lang_field: str, expected_languages: list[Language]) -> None:
    languages, notes = extract_languages(_make_record(lang_field))
    assert languages == expected_languages
    assert notes == []


# --- Language tag cases -------------------------------------------------------


@pytest.mark.parametrize(
    "lang_field, expected_languages",
    [
        (
            "<language>French</language>",
            [Language(label="French", id="fre")],
        ),
        # Case with multiple languages with matching langcodes
        (
            '<language langcode="ger">German, </language><language langcode="fre">French, </language>',
            [Language(label="German", id="ger"), Language(label="French", id="fre")],
        ),
    ],
)
def test_language_tags(lang_field: str, expected_languages: list[Language]) -> None:
    languages, notes = extract_languages(_make_record(lang_field))
    assert languages == expected_languages
    assert notes == []


# --- Fuzzy matches ------------------------------------------------------------


@pytest.mark.parametrize(
    "lang_field, expected_languages",
    [
        ("Portguese", [Language(label="Portuguese", id="por")]),
        ("Potuguese", [Language(label="Portuguese", id="por")]),
        ("Lugandan", [Language(label="Luganda", id="lug")]),
        ("Swiss-German", [Language(label="Swiss German", id="gsw")]),
        ("Eng", [Language(label="English", id="eng")]),
        ("Language", []),
        (
            "English and Norweigan",
            [
                Language(label="English", id="eng"),
                Language(label="Norwegian", id="nor"),
            ],
        ),
        (
            "English, Portugese, French and Spanish",
            [
                Language(label="English", id="eng"),
                Language(label="Portuguese", id="por"),
                Language(label="French", id="fre"),
                Language(label="Spanish", id="spa"),
            ],
        ),
    ],
)
def test_fuzzy_cases(lang_field: str, expected_languages: list[Language]) -> None:
    languages, notes = extract_languages(_make_record(lang_field))
    assert languages == expected_languages
    assert notes == []


# --- Fallback: note produced --------------------------------------------------


@pytest.mark.parametrize(
    "lang_field, expected_languages",
    [
        (
            "Partly in German, partly in English, some articles in French.",
            [
                Language(label="German", id="ger"),
                Language(label="English", id="eng"),
                Language(label="French", id="fre"),
            ],
        ),
        ("Nigerian", []),
    ],
)
def test_fallback_cases(lang_field: str, expected_languages: list[Language]) -> None:
    languages, notes = extract_languages(_make_record(lang_field))
    assert languages == expected_languages
    assert notes == [_language_note(lang_field)]


# --- Spelling corrections -----------------------------------------------------


def test_fixes_spelling_errors() -> None:
    # Taken from f9f09f42-675d-4d27-8efa-1726d314f20b
    # We can remove this test and the fixup code once the record is corrected.
    record = _make_record(
        "The majority of this collection is in English, however Kitzinger recieved "
        "letters from around the world and travelled widely for conferences so some "
        "material is not."
    )
    languages, notes = extract_languages(record)

    assert languages == [Language(label="English", id="eng")]
    assert notes == [
        _language_note(
            "The majority of this collection is in English, however Kitzinger received "
            "letters from around the world and travelled widely for conferences so some "
            "material is not."
        )
    ]


# --- Multiple field values ----------------------------------------------------


def test_combines_languages_and_notes_from_multiple_values() -> None:
    record = _make_record(
        "English; German",
        "French with a Polish translation",
        "Dutch",
        "Chinese inscription",
    )
    languages, notes = extract_languages(record)

    assert languages == [
        Language(label="English", id="eng"),
        Language(label="German", id="ger"),
        Language(label="French", id="fre"),
        Language(label="Polish", id="pol"),
        Language(label="Dutch", id="dut"),
        Language(label="Chinese", id="chi"),
    ]
    assert notes == [
        _language_note("French with a Polish translation"),
        _language_note("Chinese inscription"),
    ]


def test_deduplicates_languages_and_notes() -> None:
    record = _make_record(
        "English; Chinese",
        "Chinese inscription",
        "Chinese inscription",
    )
    languages, notes = extract_languages(record)

    assert languages == [
        Language(label="English", id="eng"),
        Language(label="Chinese", id="chi"),
    ]
    assert notes == [_language_note("Chinese inscription")]
