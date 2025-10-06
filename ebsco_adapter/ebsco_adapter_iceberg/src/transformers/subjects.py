import logging

from pymarc.field import Field
from pymarc.record import Record

from models.work import ConceptType, SourceConcept, SourceIdentifier, Subject
from transformers.common import (
    non_empty,
    normalise_identifier_value,
    subdivision_concepts,
)

SUBJECT_FIELDS = ["600", "610", "611", "648", "650", "651"]
logger: logging.Logger = logging.getLogger(__name__)
FIELD_TO_TYPE: dict[str, ConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}
SUBDIVISION_SUBFIELDS: list[str] = ["v", "x", "y", "z"]


def extract_subjects(record: Record) -> list[Subject]:
    return non_empty(
        extract_subject(field) for field in record.get_fields(*SUBJECT_FIELDS)
    )


def extract_subject(field: Field) -> Subject | None:
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0 or not "".join(
        subfield.strip() for subfield in a_subfields
    ):
        return None
    if len(a_subfields) > 1:
        logger.error(f"Repeated Non-repeating field $a found in {field.tag} field")

    if field.tag == "600":
        main_concept_label_fields = field.get_subfields(
            "a", "b", "c", "d", "t", "p", "n", "q", "l"
        )
        secondary_subfield_codes = ["x"]
        extra_label_subfield_codes = ["e", "x"]
    elif field.tag == "610":
        main_concept_label_fields = field.get_subfields("a", "b")
        secondary_subfield_codes = []
        extra_label_subfield_codes = ["c", "d", "e"]
    elif field.tag == "611":
        main_concept_label_fields = field.get_subfields("a", "c", "d")
        secondary_subfield_codes = []
        extra_label_subfield_codes = []
    else:
        main_concept_label_fields = a_subfields
        secondary_subfield_codes = SUBDIVISION_SUBFIELDS
        extra_label_subfield_codes = secondary_subfield_codes

    label = " ".join(
        main_concept_label_fields + field.get_subfields(*extra_label_subfield_codes)
    )
    main_concept_label = " ".join(main_concept_label_fields)

    return Subject(
        label=label,
        concepts=[
            SourceConcept(
                label=main_concept_label,
                type=FIELD_TO_TYPE.get(field.tag, "Concept"),
                id=SourceIdentifier(
                    identifier_type="label-derived",
                    ontology_type=FIELD_TO_TYPE.get(field.tag, "Concept"),
                    value=normalise_identifier_value(main_concept_label),
                ),
            )
        ]
        + subdivision_concepts(field, secondary_subfield_codes),
    )
