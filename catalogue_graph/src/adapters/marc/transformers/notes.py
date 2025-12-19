"""
Extract exhibition note from field
585 - Exhibition Note
https://www.loc.gov/marc/bibliographic/bd585.html
"""

from pymarc.record import Record
from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

from adapters.ebsco.transformers.common import get_a_subfields

exhibitions_note_type = IdLabel(id="exhibitions-note", label="Exhibitions note")


def extract_notes(record: Record) -> list[Note]:
    return [
        Note(contents=a_value, note_type=exhibitions_note_type)
        for a_value in get_a_subfields("585", record)
    ]
