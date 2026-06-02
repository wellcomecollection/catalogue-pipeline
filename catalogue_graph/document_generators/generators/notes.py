from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

from .random import random_alphanumeric, rng


def create_note() -> Note:
    note_types = [
        IdLabel(id="general-note", label="Notes"),
        IdLabel(id="funding-information", label="Funding information"),
        IdLabel(id="location-of-duplicates-note", label="Location of duplicates"),
    ]
    return Note(
        note_type=rng.choice(note_types),
        contents=random_alphanumeric(),
    )
