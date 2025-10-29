import logging
from collections.abc import Generator

from models.pipeline.concept import Concept, Subject
from models.pipeline.identifier import Identifiable
from pymarc.field import Field
from pymarc.record import Record
from utils.types import RawConceptType

from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    SUBDIVISION_CODES,
    SUBFIELD_TYPE_MAP,
    build_concept,
)

logger: logging.Logger = logging.getLogger(__name__)


def _get_main_label(field: Field) -> str:
    main_label_codes = MAIN_LABEL_SUBFIELDS.get(field.tag, ["a"])
    main_labels = field.get_subfields(*main_label_codes)
    return " ".join(main_labels)


def label_transform_600(field: Field) -> str:
    main_label = _get_main_label(field)
    role_subfields = field.get_subfields("e")
    subdivision_labels = field.get_subfields("x")

    # base label (a..l minus x) + role subfield e (if present)
    base_label_part = " ".join([main_label] + role_subfields)

    # x subdivision appended with hyphen separator
    return " - ".join([base_label_part] + subdivision_labels)


def label_transform_610(field: Field) -> str:
    # original space-joined label including c,d,e; ignore subdivisions for label & concepts
    main_label = _get_main_label(field)
    extra_labels = field.get_subfields("c", "d", "e")
    return " ".join([main_label] + extra_labels)


def label_transform_611(field: Field) -> str:
    return _get_main_label(field)


def label_transform_648_650_651(field: Field) -> str:
    # a + all subdivision subfields joined with hyphen separator (Scala style)
    main_label = _get_main_label(field)
    subdivision_labels = field.get_subfields(*SUBDIVISION_CODES)
    return " - ".join([main_label] + subdivision_labels)


def subdivision_concepts_600(field: Field) -> Generator[Concept]:
    # Only x yields a subdivision concept
    for raw_label in field.get_subfields("x"):
        yield build_concept(raw_label, "Concept")


def subdivision_concepts_648_650_651(field: Field) -> Generator[Concept]:
    for subfield in field.subfields:
        if subfield.code in SUBDIVISION_CODES:
            ontology_type = SUBFIELD_TYPE_MAP.get(subfield.code, "Concept")
            yield build_concept(subfield.value, ontology_type)


SUBJECT_FIELDS = ["600", "610", "611", "648", "650", "651"]
FIELD_TO_TYPE: dict[str, RawConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}
MAIN_LABEL_SUBFIELDS = {
    "600": ["a", "b", "c", "d", "t", "p", "n", "q", "l"],
    "610": ["a", "b"],
    "611": ["a", "c", "d"],
}
LABEL_TRANSFORMS = {
    "600": label_transform_600,
    "610": label_transform_610,
    "611": label_transform_611,
    "648": label_transform_648_650_651,
    "650": label_transform_648_650_651,
    "651": label_transform_648_650_651,
}
SUBDIVISION_TRANSFORMS = {
    "600": subdivision_concepts_600,
    "610": lambda _: [],
    "611": lambda _: [],
    "648": subdivision_concepts_648_650_651,
    "650": subdivision_concepts_648_650_651,
    "651": subdivision_concepts_648_650_651,
}


def extract_subjects(record: Record) -> list[Subject]:
    return non_empty(
        extract_subject(field) for field in record.get_fields(*SUBJECT_FIELDS)
    )


def extract_subject(field: Field) -> Subject | None:
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0 or not "".join(s.strip() for s in a_subfields):
        return None
    if len(a_subfields) > 1:
        logger.error(f"Repeated Non-repeating field $a found in {field.tag} field")

    # Concept construction with original semantics (preserving Python rules while adopting separator changes)
    main_label = _get_main_label(field)
    ontology_type = FIELD_TO_TYPE.get(field.tag, "Concept")
    primary_concept = build_concept(main_label, ontology_type)

    get_subdivision_concepts = SUBDIVISION_TRANSFORMS[field.tag]
    get_label = LABEL_TRANSFORMS[field.tag]
    label = get_label(field)

    # Trim trailing period from final subject label (Scala behaviour)
    return Subject(
        label=label.rstrip("."),
        id=Identifiable.identifier_from_text(label, "Subject"),
        concepts=[primary_concept] + list(get_subdivision_concepts(field)),
    )
