from pymarc.record import Record

from adapters.transformers.axiell.organisation_and_arrangement import (
    extract_arrangement,
)
from adapters.transformers.axiell.terms_of_use import extract_terms_of_use
from adapters.transformers.marc.notes import (
    ARRANGEMENT_NOTE,
    TERMS_OF_USE,
    _create_note,
)
from adapters.transformers.marc.notes import extract_notes as base_extract_notes
from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

FINDING_AIDS = IdLabel(id="finding-aids", label="Finding aids")


def extract_notes(record: Record) -> list[Note]:
    """Extract base notes using shared MARC logic, plus notes specific to Axiell data"""
    # Exclude MARC 506 and 540. They are handled below using custom 'Terms of Use' note logic.
    notes = base_extract_notes(record, exclude_fields=["506", "540"])

    # We use MARC 590 to store custom 'finding aids' notes
    for field in record.get_fields("590"):
        finding_aids_note = _create_note(field, note_type=FINDING_AIDS)
        if finding_aids_note:
            notes.append(finding_aids_note)

    # The arrangement note is extracted from 351 $b.
    arrangement = extract_arrangement(record)
    if arrangement:
        notes.append(Note(contents=arrangement, note_type=ARRANGEMENT_NOTE))

    terms_of_use = extract_terms_of_use(record)
    if terms_of_use:
        notes.append(Note(contents=terms_of_use, note_type=TERMS_OF_USE))

    return notes
