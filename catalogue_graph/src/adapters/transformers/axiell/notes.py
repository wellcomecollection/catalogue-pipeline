from pymarc.record import Record

from adapters.transformers.axiell.organisation_and_arrangement import (
    extract_arrangement,
)
from adapters.transformers.marc.notes import ARRANGEMENT_NOTE, _create_note
from adapters.transformers.marc.notes import extract_notes as base_extract_notes
from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

FINDING_AIDS = IdLabel(id="finding-aids", label="Finding aids")


def extract_notes(record: Record) -> list[Note]:
    notes = base_extract_notes(record, exclude_fields=["506", "540"])

    for field in record.get_fields("590"):
        finding_aids_note = _create_note(field, note_type=FINDING_AIDS)
        if finding_aids_note:
            notes.append(finding_aids_note)

    arrangement = extract_arrangement(record)
    if arrangement:
        notes.append(Note(contents=arrangement, note_type=ARRANGEMENT_NOTE))

    return notes
