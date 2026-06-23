from pymarc.record import Record

from adapters.transformers.marc.notes import _create_note
from adapters.transformers.marc.notes import extract_notes as base_extract_notes
from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

FINDING_AIDS = IdLabel(id="finding-aids", label="Finding aids")


def extract_notes(record: Record) -> list[Note]:
    notes = base_extract_notes(record, exclude_fields=["506", "540"])

    for field in record.get_fields("590"):
        _create_note(field, note_type=FINDING_AIDS)

    return notes
