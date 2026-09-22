"""Which of online, open shelves and closed stores a work's locations make it available from."""

from models.pipeline.id_label import Id
from models.pipeline.note import Note
from models.pipeline.work_data import WorkData

OTHER_INSTITUTION_TERMS = (
    "Churchill Archives Centre",
    "UCL Special Collections and Archives",
    "at King's College London",
    "at the Army Medical Services Museum",
    "currently remains with the Martin Leake family",
)


def availabilities(data: WorkData) -> list[Id]:
    locations = [location for item in data.items for location in item.locations]
    locations += [holding.location for holding in data.holdings if holding.location]
    in_other_library = any(_is_in_other_library(note) for note in data.notes)

    found: list[Id] = []
    for location in locations:
        availability = location.availability(in_other_library)
        if availability is not None and availability not in found:
            found.append(availability)
    return found


def _is_in_other_library(note: Note) -> bool:
    """Crude matching on terms of use, as the Scala pipeline does; see platform issue 5190."""
    if note.note_type.id != "terms-of-use":
        return False
    terms = note.contents
    lowered = terms.lower()
    if "available at" in lowered or "available by appointment at" in lowered:
        return True
    return any(phrase in terms for phrase in OTHER_INSTITUTION_TERMS)
