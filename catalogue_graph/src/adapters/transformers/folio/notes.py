from pymarc.field import Field, Subfield
from pymarc.record import Record

from adapters.transformers.marc.common import UKLW_PREFIX_RE
from adapters.transformers.marc.notes import (
    COPYRIGHT_NOTE,
    GENERAL_NOTE,
    RELATED_MATERIAL,
    _create_note,
)
from adapters.transformers.marc.notes import extract_notes as base_extract_notes
from models.pipeline.note import Note

SEARCH_URL = "https://wellcomecollection.org/search/works?query="


def _link_catalogue_reference(value: str) -> str:
    """Turn a "(UkLW)b…" reference into a link to a catalogue search for it."""
    if match := UKLW_PREFIX_RE.fullmatch(value):
        bnumber = match.group(1).strip()
        return f'(<a href="{SEARCH_URL}{bnumber}">{bnumber}</a>)'
    return value


def _related_material_note(field: Field) -> Note | None:
    linked = Field(
        tag=field.tag,
        indicators=field.indicators,
        subfields=[
            Subfield(code, _link_catalogue_reference(value) if code == "w" else value)
            for code, value in field
        ],
    )
    return _create_note(linked, RELATED_MATERIAL)


def _local_note(field: Field) -> Note | None:
    # ǂ0 is the item barcode (ǂ9 before the FOLIO migration), not for display
    return _create_note(field, GENERAL_NOTE, suppressed_subfields={"0", "9"})


def extract_notes(record: Record) -> list[Note]:
    """Shared MARC notes plus the local 591/593 notes and 787 related material."""
    notes = base_extract_notes(
        record,
        # Excluded to match the Sierra transformer, which does not produce these notes
        exclude_fields=["541", "583", "584"],
        extra_fields={
            "787": _related_material_note,
            "591": _local_note,
            "593": lambda field: _create_note(field, COPYRIGHT_NOTE),
        },
    )
    for note in notes:
        note.contents = note.contents.strip()
    return notes
