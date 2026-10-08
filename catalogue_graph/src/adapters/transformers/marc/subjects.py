from collections.abc import Callable, Generator

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.authority_standard_number import extract_identifier
from adapters.transformers.marc.common import distinct, non_empty
from adapters.transformers.marc.concepts import (
    SUBDIVISION_CODES,
    SUBFIELD_TYPE_MAP,
    build_concept,
)
from adapters.transformers.utils.text_utils import (
    trim_trailing,
    trim_trailing_period,
)
from models.pipeline.concept import Concept, Subject
from models.pipeline.identifier import Identifiable
from utils.types import RawConceptType

logger = structlog.get_logger(__name__)


def _get_main_label(field: Field) -> str:
    main_label_codes = MAIN_LABEL_SUBFIELDS.get(field.tag, ["a"])
    values = field.get_subfields(*main_label_codes)
    return " ".join(value for value in values if value.strip())


def _primary_concept_label(field: Field) -> str:
    """The primary concept's label: the main label trimmed by heading type."""
    return CONCEPT_LABEL_TRANSFORMS[field.tag](_get_main_label(field))


def label_transform_600(field: Field) -> str:
    # The role (ǂe) and general subdivisions (ǂx) follow the name
    roles = field.get_subfields("e")
    subdivisions = field.get_subfields("x")
    return " ".join([_get_main_label(field), *roles, *subdivisions])


def label_transform_610(field: Field) -> str:
    # The location, date and relator term (ǂc ǂd ǂe) follow the name
    qualifiers = field.get_subfields("c", "d", "e")
    return " ".join([_get_main_label(field), *qualifiers])


def label_transform_611(field: Field) -> str:
    return _get_main_label(field)


def label_transform_648_650_651(field: Field) -> str:
    subdivisions = field.get_subfields(*SUBDIVISION_CODES)
    return " - ".join([_get_main_label(field), *subdivisions])


def subdivision_concepts_600(field: Field) -> Generator[Concept]:
    # Only x yields a subdivision concept, with its label as catalogued
    for raw_label in field.get_subfields("x"):
        yield build_concept(
            raw_label, "Concept", is_identifiable=False, label=raw_label
        )


def subdivision_concepts_648_650_651(field: Field) -> Generator[Concept]:
    for subfield in field.subfields:
        if subfield.code in SUBDIVISION_CODES:
            ontology_type = SUBFIELD_TYPE_MAP.get(subfield.code, "Concept")
            yield build_concept(
                subfield.value,
                ontology_type,
                label=trim_trailing_period(subfield.value),
            )


# Subjects are listed by heading type in this order purely for parity with the Scala pipeline
FIELD_GROUPS = [["650", "648", "651"], ["600"], ["610"], ["611"]]
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
# The trailing punctuation each heading type trims, as in the Scala pipeline
CONCEPT_LABEL_TRANSFORMS: dict[str, Callable[[str], str]] = {
    "600": lambda label: label,
    "610": lambda label: trim_trailing_period(trim_trailing(label, ",")),
    "611": lambda label: trim_trailing(label, ","),
    "648": trim_trailing_period,
    "650": trim_trailing_period,
    "651": lambda label: trim_trailing(trim_trailing_period(label), ":"),
}
SUBDIVISION_TRANSFORMS = {
    "600": subdivision_concepts_600,
    "610": lambda _: [],
    "611": lambda _: [],
    "648": subdivision_concepts_648_650_651,
    "650": subdivision_concepts_648_650_651,
    "651": subdivision_concepts_648_650_651,
}


def is_subject_to_keep(field: Field) -> bool:
    """
    Whether the heading is from LCSH or LC Names, from MeSH, or from one of the
    ǂ2 vocabularies we have adopted. The second indicator names the thesaurus:
    https://www.loc.gov/marc/bibliographic/bd650.html
    """
    return field.indicators is not None and (
        field.indicators.second in ["0", "2"]
        or (
            field.indicators.second == "7"
            and field.get("2") in ["local", "homoit", "indig", "enslv"]
        )
    )


def is_from_standard_thesaurus(field: Field) -> bool:
    """
    Whether the heading's second indicator names its thesaurus itself rather
    than deferring to ǂ2. This is the rule of the Scala Sierra transformer,
    which drops every ǂ2-sourced heading.
    """
    return field.indicators is not None and field.indicators.second != "7"


def extract_subjects(
    record: Record, keep: Callable[[Field], bool] = is_subject_to_keep
) -> list[Subject]:
    """The subjects of the 6xx headings that `keep` accepts, by heading type, without repeats."""
    return distinct(
        non_empty(
            extract_subject(field)
            for group in FIELD_GROUPS
            for field in record.get_fields(*group)
            if keep(field)
        )
    )


def extract_subject(field: Field) -> Subject | None:
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0 or not "".join(s.strip() for s in a_subfields):
        return None
    if len(a_subfields) > 1:
        logger.error(
            "Repeated non-repeating subfield $a",
            tag=field.tag,
            count=len(a_subfields),
        )

    ontology_type = FIELD_TO_TYPE.get(field.tag, "Concept")
    label = LABEL_TRANSFORMS[field.tag](field)
    # Person labels keep their trailing period, as in the Scala pipeline
    if field.tag != "600":
        label = trim_trailing_period(label)

    identifier = extract_identifier(field, ontology_type)
    subject_id = identifier or Identifiable.identifier_from_text(label, ontology_type)
    subdivision_concepts = list(SUBDIVISION_TRANSFORMS[field.tag](field))

    # A sole concept shares the subject's identifier. In a subdivided concept heading the ǂ0 names the whole heading
    # so the primary concept is label-derived. In a subdivided personal name heading it names the person.
    primary_identifier: Identifiable | None
    if not subdivision_concepts:
        primary_identifier = subject_id
    elif field.tag == "600":
        primary_identifier = identifier
    else:
        primary_identifier = None

    primary_label = _primary_concept_label(field)
    primary_concept = build_concept(
        primary_label,
        ontology_type,
        identifier=primary_identifier,
        label=primary_label,
    )

    return Subject(
        label=label,
        id=subject_id,
        concepts=[primary_concept, *subdivision_concepts],
    )
