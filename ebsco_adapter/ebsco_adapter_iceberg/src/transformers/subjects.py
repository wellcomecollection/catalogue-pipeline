import logging

from pymarc.record import Record
from pymarc.field import Field

from models.work import SourceConcept, Subject, SourceIdentifier
from transformers.common import non_empty, subdivision_concepts, normalise_identifier_value

SUBJECT_FIELDS = ["600", "610", "611", "648", "650", "651"]
logger: logging.Logger = logging.getLogger(__name__)
FIELD_TO_TYPE = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",

}
SUBDIVISION_SUBFIELDS: list[str] = ["v", "x", "y", "z"]


def extract_subjects(record: Record) -> list[Subject]:
    return non_empty((extract_subject(field) for field in record.get_fields(*SUBJECT_FIELDS)))


def extract_subject(field: Field) -> Subject | None:
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0:
        return None
    if len(a_subfields) > 1:
        logger.error("Repeated Non-repeating field $a found in 600 field")

    label = " ".join(a_subfields + field.get_subfields(*SUBDIVISION_SUBFIELDS))
    return Subject(
        label=label,
        concepts=[
                     SourceConcept(
                         label=" ".join(a_subfields),
                         type=FIELD_TO_TYPE.get(field.tag, "Concept"),
                         id=SourceIdentifier(
                             identifier_type="label-derived",
                             ontology_type="Concept",
                             value=normalise_identifier_value(" ".join(a_subfields)),
                         )
                     )
                 ] + subdivision_concepts(field, SUBDIVISION_SUBFIELDS)
    )
