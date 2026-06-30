import re

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from adapters.transformers.marc.notes import LANGUAGE_NOTE
from lookups.languages import from_name
from models.pipeline.id_label import Language
from models.pipeline.note import Note

_SEPARATORS = re.compile(r"\n|;|\.|,|/|\band\b|`")
_LANGUAGE_TAG_PATTERN = re.compile(r'<language(?: langcode="[a-z]+")?>(.*?)</language>')
_LANGUAGE_NAME_PATTERN = re.compile(r"[A-Z][a-z]+")

# TODO: These corrections are ported from the Scala pipeline. It would be preferable to fix these in the source system.
_FUZZY_CORRECTIONS = [
    ("Portugese", "Portuguese"),
    ("Portguese", "Portuguese"),
    ("Potuguese", "Portuguese"),
    ("Portugeuse", "Portuguese"),
    ("Swiss-German", "Swiss German"),
    ("Norweigan", "Norwegian"),
    ("Lugandan", "Luganda"),
    ("Enlgish", "English"),
    ("Itallian", "Italian"),
    ("Russain", "Russian"),
    ("Gujerati", "Gujarati"),
    ("Chipewayan", "Chipewyan"),
    ("Fante", "Fanti"),
]


def extract_languages(record: Record) -> tuple[list[Language], list[Note]]:
    """Parse 546 $a language field values into a list of Languages and a list of Notes.

    If the field can be completely parsed as a list of languages, the original
    text is discarded. If not, any recognisable languages are extracted and the
    original text is kept verbatim as a LanguageNote.
    """
    languages: list[Language] = []
    notes: list[Note] = []

    for value in non_empty_subfields("546", "a", record):
        new_languages, new_notes = _parse_single_value(value)
        for lang in new_languages:
            if lang not in languages:
                languages.append(lang)
        for note in new_notes:
            if note not in notes:
                notes.append(note)

    return languages, notes


def _parse_single_value(lang_field: str) -> tuple[list[Language], list[Note]]:
    parsed = _parse_as_language_list(lang_field)
    if parsed is not None:
        return parsed, []

    # Unable to parse the whole string - find any languages and keep the full text as a note.
    return (
        _find_languages_in_text(lang_field),
        [
            Note(
                # TODO: This spelling correction is ported from Scala and affects exactly one record
                # (collect:110159658). It would be preferable to fix this in the source system.
                contents=lang_field.replace("recieved", "received"),
                note_type=LANGUAGE_NOTE,
            )
        ],
    )


def _parse_as_language_list(lang_field: str) -> list[Language] | None:
    """
    Try to parse the whole string as a list of languages.
    If the string contains non-language components, return None.
    """
    for matcher in (
        _match_exact,
        _match_after_corrections,
        _match_after_stripping_tags,
    ):
        result = matcher(lang_field)
        if result is not None:
            return result
    return None


def _match_exact(lang_field: str) -> list[Language] | None:
    """Split the string using `_SEPARATORS` and then try to match each component to a language name exactly."""
    components = [
        part.strip() for part in _SEPARATORS.split(lang_field) if part.strip()
    ]
    matched = [lang for part in components if (lang := from_name(part))]

    if len(matched) == len(components):
        return matched
    return None


def _match_after_corrections(lang_field: str) -> list[Language] | None:
    """Fix misspelled languages and then retry `_parse_as_language_list`."""
    corrected = lang_field
    for wrong, right in _FUZZY_CORRECTIONS:
        corrected = corrected.replace(wrong, right)
    corrected = re.sub(r"^Eng$", "English", corrected)
    corrected = re.sub(r"^Language$", "", corrected)

    if corrected != lang_field:
        return _parse_as_language_list(corrected)
    return None


def _match_after_stripping_tags(lang_field: str) -> list[Language] | None:
    """Remove XML tags surrounding language names and then retry `_parse_as_language_list`."""
    tagless = _LANGUAGE_TAG_PATTERN.sub(r"\1", lang_field)
    if tagless != lang_field:
        return _parse_as_language_list(tagless)
    return None


def _find_languages_in_text(lang_field: str) -> list[Language]:
    """Identify capitalised words and try to match them to language names, returning all matches."""
    return [
        lang
        for word in _LANGUAGE_NAME_PATTERN.findall(lang_field)
        if (lang := from_name(word))
    ]
