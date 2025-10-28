import logging

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    SUBDIVISION_CODES,
    SUBFIELD_TYPE_MAP,
)
from adapters.ebsco.transformers.text_utils import (
    normalise_identifier_value,
    normalise_label,
)
from models.pipeline.concept import Concept, Subject
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier
from utils.types import ConceptType

SUBJECT_FIELDS = ["600", "610", "611", "648", "650", "651"]
logger: logging.Logger = logging.getLogger(__name__)
FIELD_TO_TYPE: dict[str, ConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}


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
        secondary_subfield_codes = SUBDIVISION_CODES
        extra_label_subfield_codes = secondary_subfield_codes

    # Label construction rules:
    # - 600: base label (a..l minus x) + role subfield e (if present) space-separated; x subdivision appended with hyphen separator
    # - 610 & 611: original space-joined label including c,d,e (610) / none extra (611); ignore subdivisions for label & concepts
    # - 648/650/651: a + all subdivision subfields joined with hyphen separator (Scala style)
    primary_label_part = " ".join(main_concept_label_fields)
    subdivision_label_parts = field.get_subfields(*secondary_subfield_codes)
    if field.tag == "600":
        role_subfields = field.get_subfields("e")
        base_label_part = " ".join(main_concept_label_fields + role_subfields)
        if subdivision_label_parts:
            label = " - ".join([base_label_part] + subdivision_label_parts)
        else:
            label = base_label_part
    elif field.tag in ["610", "611"]:
        # Space-joined including extra label subfields (c,d,e) for 610 and none extra for 611
        label = " ".join(
            main_concept_label_fields + field.get_subfields(*extra_label_subfield_codes)
        )
    else:
        if subdivision_label_parts:
            label = " - ".join([primary_label_part] + subdivision_label_parts)
        else:
            label = primary_label_part
    main_concept_label = " ".join(main_concept_label_fields)

    # Concept construction with original semantics (preserving Python rules while adopting separator changes)
    concepts: list[Concept] = [build_primary_concept(field, main_concept_label)]
    if field.tag == "600":
        # Only x yields a subdivision concept
        for subfield in field.subfields:
            if subfield.code == "x":
                label_part = normalise_label(subfield.value, "Concept")
                concepts.append(
                    Concept(
                        label=label_part,
                        type="Concept",
                        id=Identifiable.from_source_identifier(
                            SourceIdentifier(
                                identifier_type=Id(id="label-derived"),
                                ontology_type="Concept",
                                value=normalise_identifier_value(label_part),
                            )
                        ),
                    )
                )
    elif field.tag in ["648", "650", "651"]:
        for subfield in field.subfields:
            code = getattr(subfield, "code", "")
            if code in SUBDIVISION_CODES:
                ontology_type = SUBFIELD_TYPE_MAP.get(code, "Concept")
                label_part = normalise_label(subfield.value, ontology_type)
                ontology_type = SUBFIELD_TYPE_MAP.get(code, "Concept")
                concepts.append(
                    Concept(
                        label=label_part,
                        type=ontology_type,
                        id=Identifiable.from_source_identifier(
                            SourceIdentifier(
                                identifier_type=Id(id="label-derived"),
                                ontology_type=ontology_type,
                                value=normalise_identifier_value(label_part),
                            )
                        ),
                    )
                )
    # 610 & 611: no additional subdivision concepts
    # Trim trailing period from final subject label (Scala behaviour)
    return Subject(label=label.rstrip("."), concepts=concepts)


def build_primary_concept(field: Field, label: str) -> Concept:
    ontology_type = FIELD_TO_TYPE.get(field.tag, "Concept")
    # Apply type-specific normalisation to the label.
    label = normalise_label(label, ontology_type)
    return Concept(
        label=label,
        type=ontology_type,
        id=Identifiable.from_source_identifier(
            SourceIdentifier(
                identifier_type=Id(id="label-derived"),
                ontology_type=ontology_type,
                value=normalise_identifier_value(label),
            )
        ),
    )
