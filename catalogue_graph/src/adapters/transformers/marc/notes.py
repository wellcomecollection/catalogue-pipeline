"""
Extract notes from 5xx MARC fields.
"""

from collections.abc import Callable
from urllib.parse import urlparse

import structlog
from pymarc.field import Field
from pymarc.record import Record

from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

logger = structlog.get_logger(__name__)

GLOBALLY_SUPPRESSED_SUBFIELDS = {"5"}

GENERAL_NOTE = IdLabel(id="general-note", label="Notes")
DISSERTATION_NOTE = IdLabel(id="dissertation-note", label="Dissertation note")
BIBLIOGRAPHICAL_INFORMATION = IdLabel(
    id="bibliographic-info", label="Bibliographic information"
)
CONTENTS_NOTE = IdLabel(id="contents", label="Contents")
TERMS_OF_USE = IdLabel(id="terms-of-use", label="Terms of use")
CREDITS_NOTE = IdLabel(id="credits", label="Creator/production credits")
REFERENCES_NOTE = IdLabel(id="references-note", label="References note")
LETTERING_NOTE = IdLabel(id="lettering-note", label="Lettering note")
NUMBERING_NOTE = IdLabel(id="numbering-note", label="Numbering note")
TIME_AND_PLACE_NOTE = IdLabel(id="time-and-place-note", label="Time and place note")
CITE_AS_NOTE = IdLabel(id="reference", label="Reference")
REPRODUCTION_NOTE = IdLabel(id="reproduction-note", label="Reproduction note")
LOCATION_OF_ORIGINAL_NOTE = IdLabel(
    id="location-of-original", label="Location of original"
)
LOCATION_OF_DUPLICATES_NOTE = IdLabel(
    id="location-of-duplicates", label="Location of duplicates"
)
FUNDING_INFORMATION = IdLabel(id="funding-info", label="Funding information")
COPYRIGHT_NOTE = IdLabel(id="copyright-note", label="Copyright note")
RELATED_MATERIAL = IdLabel(id="related-material", label="Related material")
BIOGRAPHICAL_NOTE = IdLabel(id="biographical-note", label="Biographical note")
LANGUAGE_NOTE = IdLabel(id="language-note", label="Language note")
OWNERSHIP_NOTE = IdLabel(id="ownership-note", label="Ownership note")
BINDING_INFORMATION = IdLabel(id="binding-detail", label="Binding detail")
PUBLICATIONS_NOTE = IdLabel(id="publication-note", label="Publications note")
EXHIBITIONS_NOTE = IdLabel(id="exhibitions-note", label="Exhibitions note")
AWARDS_NOTE = IdLabel(id="awards-note", label="Awards note")


def _is_url(s: str) -> bool:
    parsed = urlparse(s)
    return parsed.scheme in ("http", "https") and bool(parsed.netloc)


def _create_note_from_contents(
    field: Field,
    note_type: IdLabel,
    suppressed_subfields: set[str] | None = None,
) -> Note:
    suppressed = GLOBALLY_SUPPRESSED_SUBFIELDS | (suppressed_subfields or set())
    parts: list[str] = []
    for subfield_tag, value in field:
        if subfield_tag in suppressed:
            continue

        # Subfield ǂu is rendered as an HTML link when it contains a valid URL.
        if subfield_tag == "u":
            trimmed = value.strip()
            if _is_url(trimmed):
                parts.append(f'<a href="{trimmed}">{trimmed}</a>')
            else:
                logger.warning(
                    "Subfield ǂu which doesn't look like a URL",
                    contents=value,
                )
                parts.append(value)
        else:
            parts.append(value)
    contents = " ".join(parts)
    return Note(contents=contents, note_type=note_type)


def _create_ownership_note(field: Field) -> Note | None:
    # Only produce an ownership note when indicator 1 is "1" (not private).
    if field.indicator1 == "1":
        return _create_note_from_contents(field, OWNERSHIP_NOTE)
    return None


def _create_location_of_note(field: Field) -> Note | None:
    # Use indicator 1 to distinguish location of originals vs duplicates.
    if field.indicator1 == "2":
        return _create_note_from_contents(field, LOCATION_OF_DUPLICATES_NOTE)
    return _create_note_from_contents(field, LOCATION_OF_ORIGINAL_NOTE)


def _note_from(note_type: IdLabel) -> Callable[[Field], Note | None]:
    return lambda field: _create_note_from_contents(field, note_type)


# Mapping of MARC tag to a function that creates a Note from the field
_NOTES_FIELDS: dict[str, Callable[[Field], Note | None]] = {
    "500": _note_from(GENERAL_NOTE),
    "501": _note_from(GENERAL_NOTE),
    "502": _note_from(DISSERTATION_NOTE),
    "504": _note_from(BIBLIOGRAPHICAL_INFORMATION),
    "505": _note_from(CONTENTS_NOTE),
    "506": _note_from(TERMS_OF_USE),
    "508": _note_from(CREDITS_NOTE),
    "510": _note_from(REFERENCES_NOTE),
    "511": _note_from(CREDITS_NOTE),
    "514": _note_from(LETTERING_NOTE),
    "515": _note_from(NUMBERING_NOTE),
    "518": _note_from(TIME_AND_PLACE_NOTE),
    "524": _note_from(CITE_AS_NOTE),
    "525": _note_from(GENERAL_NOTE),
    "533": _note_from(REPRODUCTION_NOTE),
    "534": _note_from(REPRODUCTION_NOTE),
    "535": _create_location_of_note,
    "536": _note_from(FUNDING_INFORMATION),
    "540": _note_from(TERMS_OF_USE),
    "542": _note_from(COPYRIGHT_NOTE),
    "544": _note_from(RELATED_MATERIAL),
    "545": _note_from(BIOGRAPHICAL_NOTE),
    "546": _note_from(LANGUAGE_NOTE),
    "547": _note_from(GENERAL_NOTE),
    "550": _note_from(GENERAL_NOTE),
    "561": _create_ownership_note,
    "562": _note_from(GENERAL_NOTE),
    "563": _note_from(BINDING_INFORMATION),
    "580": _note_from(GENERAL_NOTE),
    "581": _note_from(PUBLICATIONS_NOTE),
    "585": _note_from(EXHIBITIONS_NOTE),
    "586": _note_from(AWARDS_NOTE),
    "588": _note_from(GENERAL_NOTE),
}


def extract_notes(record: Record) -> list[Note]:
    notes: list[Note] = []
    for field in record.get_fields(*_NOTES_FIELDS.keys()):
        create = _NOTES_FIELDS.get(field.tag)
        if create is None:
            continue
        note = create(field)
        if note is not None and note.contents.strip():
            notes.append(note)
    return notes
