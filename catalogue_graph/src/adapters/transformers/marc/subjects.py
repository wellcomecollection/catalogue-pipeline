from collections import defaultdict
from collections.abc import Callable, Generator, Sequence

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
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

logger = structlog.get_logger(__name__)


def _primary_labels(field: Field) -> list[str]:
    """
    The label of each primary concept: one per ǂa on a concept heading, or the
    joined name subfields on a name heading, as in the Scala pipeline.

    A repeat of a non-repeatable subfield is logged as an error and dropped
    when it duplicates an earlier value. Otherwise it is kept, despite being
    invalid MARC, to mirror Scala: in existing data the repeat holds useful
    information, such as a second title or language.
    """
    main_label_codes = MAIN_LABEL_SUBFIELDS.get(field.tag, ["a"])
    non_repeatable_codes = NON_REPEATABLE_SUBFIELDS.get(field.tag, ["a"])
    values = []
    seen: defaultdict[str, set[str]] = defaultdict(set)
    for code, value in field:
        if code not in main_label_codes or not value.strip():
            continue
        if code in non_repeatable_codes and seen[code]:
            logger.error(
                f"Repeated non-repeating subfield ${code}",
                tag=field.tag,
                field=str(field),
            )
            if value.strip() in seen[code]:
                continue
        seen[code].add(value.strip())
        values.append(value)

    # A name heading has one primary concept, a concept heading one per ǂa
    return [" ".join(values)] if field.tag in NAME_HEADINGS else values


def label_transform_600(field: Field, main_label: str) -> str:
    # The role (ǂe) and general subdivisions (ǂx) follow the name
    roles = field.get_subfields("e")
    subdivisions = field.get_subfields("x")
    return " ".join([main_label, *roles, *subdivisions])


def label_transform_610(field: Field, main_label: str) -> str:
    # The location, date and relator term (ǂc ǂd ǂe) follow the name
    qualifiers = field.get_subfields("c", "d", "e")
    return " ".join([main_label, *qualifiers])


def label_transform_611(field: Field, main_label: str) -> str:
    return main_label


def label_transform_648_650_651(field: Field, main_label: str) -> str:
    subdivisions = field.get_subfields(*SUBDIVISION_CODES)
    return " - ".join([main_label, *subdivisions])


def subdivision_concepts_600(field: Field) -> Generator[Concept]:
    # Only x yields a subdivision concept, with its label as catalogued
    for raw_label in field.get_subfields("x"):
        yield build_concept(raw_label, "Concept", Unidentifiable())


def subdivision_concepts_648_650_651(field: Field) -> Generator[Concept]:
    for subfield in field.subfields:
        if subfield.code in SUBDIVISION_CODES:
            ontology_type = SUBFIELD_TYPE_MAP.get(subfield.code, "Concept")
            yield build_concept(trim_trailing_period(subfield.value), ontology_type)


SUBJECT_TAGS = ["600", "610", "611", "648", "650", "651"]
# The Sierra transformer lists subjects by heading type: concept headings, then
# personal, corporate and meeting names, each group in document order
SIERRA_TAG_ORDER = [["650", "648", "651"], ["600"], ["610"], ["611"]]
FIELD_TO_TYPE: dict[str, RawConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}
# Personal, corporate and meeting names; the other headings are concepts, periods and places
NAME_HEADINGS = ["600", "610", "611"]
MAIN_LABEL_SUBFIELDS = {
    "600": ["a", "b", "c", "d", "t", "p", "n", "q", "l"],
    "610": ["a", "b"],
    "611": ["a", "c", "d"],
}
# The main label subfields that MARC does not allow to repeat, per heading type
NON_REPEATABLE_SUBFIELDS = {"600": ["a", "b", "d", "t", "q", "l"]}
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


def is_from_adopted_vocabulary(field: Field) -> bool:
    """
    True for headings from LCSH or LC Names, from MeSH, or from an adopted ǂ2 vocabulary.

    The second indicator names the thesaurus: https://www.loc.gov/marc/bibliographic/bd650.html
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
    True for headings whose second indicator names a thesaurus, false for those sourced from ǂ2.

    This is the rule of the Scala Sierra transformer, which drops every ǂ2-sourced heading.
    """
    return field.indicators is not None and field.indicators.second != "7"


def extract_subjects(
    record: Record,
    keep: Callable[[Field], bool] = is_from_adopted_vocabulary,
    tag_order: Sequence[Sequence[str]] | None = None,
) -> list[Subject]:
    """
    Extract a subject from each 6xx heading that `keep` accepts, deduplicated.

    Subjects follow document order, or the given groups of tags in turn, with
    document order within each group.
    """
    return distinct(
        non_empty(
            extract_subject(field)
            for group in tag_order or [SUBJECT_TAGS]
            for field in record.get_fields(*group)
            if keep(field)
        )
    )


def extract_subject(field: Field) -> Subject | None:
    primary_labels = _primary_labels(field)
    if not primary_labels:
        return None
    main_label = " - ".join(primary_labels)

    ontology_type = FIELD_TO_TYPE.get(field.tag, "Concept")
    label = LABEL_TRANSFORMS[field.tag](field, main_label)
    # Person labels keep their trailing period, as in the Scala pipeline
    if field.tag != "600":
        label = trim_trailing_period(label)

    identifier = extract_identifier(field, ontology_type)
    subject_id = identifier or Identifiable.identifier_from_text(label, ontology_type)
    subdivision_concepts = list(SUBDIVISION_TRANSFORMS[field.tag](field))

    # A sole concept shares the subject's identifier. In a subdivided concept heading the ǂ0 names the whole heading
    # so the primary concept is label-derived. In a subdivided personal name heading it names the person.
    # Several primary concepts are each label-derived; the Scala pipeline gives them all the heading's identifier.
    primary_identifier: Identifiable | None
    if len(primary_labels) == 1 and not subdivision_concepts:
        primary_identifier = subject_id
    elif field.tag == "600":
        primary_identifier = identifier
    else:
        primary_identifier = None

    primary_concepts = [
        build_concept(
            CONCEPT_LABEL_TRANSFORMS[field.tag](primary_label),
            ontology_type,
            primary_identifier,
        )
        for primary_label in primary_labels
    ]

    return Subject(
        label=label,
        id=subject_id,
        concepts=[*primary_concepts, *subdivision_concepts],
    )
