import logging

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import (
    non_empty,
    normalise_identifier_value,
    subdivision_concepts,
)
from models.pipeline.concept import Concept, Subject
from models.pipeline.identifier import Id, Identifiable, SourceIdentifier
from utils.types import RawConceptType

logger: logging.Logger = logging.getLogger(__name__)

SUBJECT_FIELDS = ["600", "610", "611", "648", "650", "651"]
FIELD_TO_TYPE: dict[str, RawConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}
SUBDIVISION_SUBFIELDS: list[str] = ["v", "x", "y", "z"]
MAIN_LABEL_SUBFIELDS = {
    "600": ["a", "b", "c", "d", "t", "p", "n", "q", "l"],
    "610": ["a", "b"],
    "611": ["a", "c", "d"],
}
SECONDARY_LABEL_SUBFIELDS = {"600": ["x"], "610": [], "611": []}
EXTRA_LABEL_SUBFIELDS = {"600": ["e", "x"], "610": ["c", "d", "e"], "611": []}


def extract_subjects(record: Record) -> list[Subject]:
    return non_empty(
        extract_subject(field) for field in record.get_fields(*SUBJECT_FIELDS)
    )


def extract_subject(field: Field) -> Subject | None:
    tag = field.tag
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0 or not "".join(s.strip() for s in a_subfields):
        return None
    if len(a_subfields) > 1:
        logger.error(f"Repeated Non-repeating field $a found in {tag} field")

    main_label_codes = MAIN_LABEL_SUBFIELDS.get(tag, ["a"])
    secondary_label_codes = SECONDARY_LABEL_SUBFIELDS.get(tag, SUBDIVISION_SUBFIELDS)
    extra_label_codes = EXTRA_LABEL_SUBFIELDS.get(tag, SUBDIVISION_SUBFIELDS)

    main_labels = field.get_subfields(*main_label_codes)
    extra_labels = field.get_subfields(*extra_label_codes)
    subject_label = " ".join(main_labels + extra_labels)
    main_concept_label = " ".join(main_labels)

    concept_type = FIELD_TO_TYPE.get(tag, "Concept")
    concept_id = get_identifier(main_concept_label, concept_type)
    subject_id = get_identifier(subject_label, "Subject")

    main_concept = Concept(label=main_concept_label, type=concept_type, id=concept_id)
    concepts = [main_concept] + subdivision_concepts(field, secondary_label_codes)
    return Subject(label=subject_label, id=subject_id, concepts=concepts)


def get_identifier(label: str, concept_type: RawConceptType) -> Identifiable:
    source_identifier = SourceIdentifier(
        identifier_type=Id(id="label-derived"),
        ontology_type=concept_type,
        value=normalise_identifier_value(label),
    )
    return Identifiable.from_source_identifier(source_identifier)
