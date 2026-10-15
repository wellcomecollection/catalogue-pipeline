from collections import defaultdict
from collections.abc import Callable, Generator, Sequence

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.authority_standard_number import extract_identifier
from adapters.transformers.marc.common import (
    distinct,
    non_blank_subfields,
    non_empty,
)
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


def primary_concept_labels(field: Field) -> list[str]:
    """
    The label of each primary concept: one per ǂa on a concept heading, or the
    joined name subfields on a name heading, as in the Scala pipeline.

    A repeat of a non-repeatable subfield is logged as an error and dropped
    when it duplicates an earlier value. Otherwise it is kept, despite being
    invalid MARC, to mirror Scala: in existing data the repeat holds useful
    information, such as a second title or language.
    """
    main_heading_codes = MAIN_HEADING_SUBFIELDS.get(field.tag, ["a"])
    non_repeatable_codes = NON_REPEATABLE_SUBFIELDS.get(field.tag, ["a"])
    values = []
    seen: defaultdict[str, set[str]] = defaultdict(set)
    for code, value in field:
        if code not in main_heading_codes or not value.strip():
            continue
        if code in non_repeatable_codes and seen[code]:
            logger.error(
                "Repeated non-repeating subfield",
                tag=field.tag,
                field=str(field),
                subfield=code,
            )
            if value.strip() in seen[code]:
                continue
        seen[code].add(value.strip())
        values.append(value)

    # A name heading has one primary concept, a concept heading one per ǂa
    if field.tag in NAME_HEADINGS and values:
        return [" ".join(values)]
    return values


def build_subject_label_600(field: Field, main_heading: str) -> str:
    # The role (ǂe) and general subdivisions (ǂx) follow the name
    roles = non_blank_subfields(field, "e")
    subdivisions = non_blank_subfields(field, "x")
    return " ".join([main_heading, *roles, *subdivisions])


def build_subject_label_610(field: Field, main_heading: str) -> str:
    # The location, date and relator term (ǂc ǂd ǂe) follow the name
    qualifiers = non_blank_subfields(field, "c", "d", "e")
    return " ".join([main_heading, *qualifiers])


def build_subject_label_611(field: Field, main_heading: str) -> str:
    return main_heading


def build_subject_label_648_650_651(field: Field, main_heading: str) -> str:
    subdivisions = non_blank_subfields(field, *SUBDIVISION_CODES)
    return " - ".join([main_heading, *subdivisions])


def build_subdivision_concepts_600(field: Field) -> Generator[Concept]:
    # Only x yields a subdivision concept, with its label as catalogued
    for raw_label in non_blank_subfields(field, "x"):
        yield build_concept(raw_label, "Concept", Unidentifiable())


def build_subdivision_concepts_648_650_651(field: Field) -> Generator[Concept]:
    for subfield in field.subfields:
        if subfield.code in SUBDIVISION_CODES and subfield.value.strip():
            concept_type = SUBFIELD_TYPE_MAP.get(subfield.code, "Concept")
            yield build_concept(trim_trailing_period(subfield.value), concept_type)


SUBJECT_TAGS = ["600", "610", "611", "648", "650", "651"]
# The Sierra transformer runs one transformer per heading type, so it lists concept
# headings first, then personal, corporate and meeting names. Within each group it
# keeps the order the Sierra API returns the fields in, which is MARC document order
SIERRA_TAG_ORDER = [["650", "648", "651"], ["600"], ["610"], ["611"]]
TAG_TO_CONCEPT_TYPE: dict[str, RawConceptType] = {
    "600": "Person",
    "610": "Organisation",
    "611": "Meeting",
    "648": "Period",
    "651": "Place",
}
# Personal, corporate and meeting names; the other headings are concepts, periods and places
NAME_HEADINGS = ["600", "610", "611"]
MAIN_HEADING_SUBFIELDS = {
    "600": ["a", "b", "c", "d", "t", "p", "n", "q", "l"],
    "610": ["a", "b"],
    "611": ["a", "c", "d"],
}
# The main heading subfields that MARC does not allow to repeat, per heading type
NON_REPEATABLE_SUBFIELDS = {"600": ["a", "b", "d", "t", "q", "l"]}
SUBJECT_LABEL_BUILDERS = {
    "600": build_subject_label_600,
    "610": build_subject_label_610,
    "611": build_subject_label_611,
    "648": build_subject_label_648_650_651,
    "650": build_subject_label_648_650_651,
    "651": build_subject_label_648_650_651,
}
# The trailing punctuation each heading type trims, as in the Scala pipeline
PRIMARY_CONCEPT_LABEL_TRIMS: dict[str, Callable[[str], str]] = {
    "600": lambda label: label,
    "610": lambda label: trim_trailing_period(trim_trailing(label, ",")),
    "611": lambda label: trim_trailing(label, ","),
    "648": trim_trailing_period,
    "650": trim_trailing_period,
    "651": lambda label: trim_trailing(trim_trailing_period(label), ":"),
}
SUBDIVISION_CONCEPT_BUILDERS = {
    "600": build_subdivision_concepts_600,
    "610": lambda _: [],
    "611": lambda _: [],
    "648": build_subdivision_concepts_648_650_651,
    "650": build_subdivision_concepts_648_650_651,
    "651": build_subdivision_concepts_648_650_651,
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
    concept_labels = primary_concept_labels(field)
    if not concept_labels:
        return None
    main_heading = " - ".join(concept_labels)

    concept_type = TAG_TO_CONCEPT_TYPE.get(field.tag, "Concept")
    subject_label = SUBJECT_LABEL_BUILDERS[field.tag](field, main_heading)
    # Person labels keep their trailing period, as in the Scala pipeline
    if field.tag != "600":
        subject_label = trim_trailing_period(subject_label)

    authority_id = extract_identifier(field, concept_type)
    subject_id = authority_id or Identifiable.identifier_from_text(
        subject_label, concept_type
    )
    subdivision_concepts = list(SUBDIVISION_CONCEPT_BUILDERS[field.tag](field))

    # The ǂ0 identifies the primary concept only when the heading is a name or a single
    # ǂa with no subdivisions. Without a ǂ0 the id is derived from the untrimmed heading,
    # as in the Scala pipeline. Otherwise the ǂ0 identifies the heading as a whole,
    # and build_concept derives each primary concept id from its own label.
    primary_concept_id: Identifiable | None
    if field.tag in NAME_HEADINGS or (
        len(concept_labels) == 1 and not subdivision_concepts
    ):
        primary_concept_id = authority_id or Identifiable.identifier_from_text(
            main_heading, concept_type
        )
    else:
        primary_concept_id = None

    primary_concepts = [
        build_concept(
            PRIMARY_CONCEPT_LABEL_TRIMS[field.tag](concept_label),
            concept_type,
            primary_concept_id,
        )
        for concept_label in concept_labels
    ]

    return Subject(
        label=subject_label,
        id=subject_id,
        concepts=[*primary_concepts, *subdivision_concepts],
    )
