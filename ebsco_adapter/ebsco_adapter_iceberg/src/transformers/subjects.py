from pymarc.field import Field
from pymarc.record import Record

from models.work import ConceptType, SourceConcept, SourceIdentifier


def extract_subjects(record: Record) -> list[SourceConcept]:
    return []
