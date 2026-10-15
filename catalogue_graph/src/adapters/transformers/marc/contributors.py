"""
Contributors from the main entry and added entry name fields: personal names
(100, 700), corporate names (110, 710) and meeting names (111, 711).

https://www.loc.gov/marc/bibliographic/bd100.html
https://www.loc.gov/marc/bibliographic/bd110.html
https://www.loc.gov/marc/bibliographic/bd111.html
https://www.loc.gov/marc/bibliographic/bd700.html
https://www.loc.gov/marc/bibliographic/bd710.html
https://www.loc.gov/marc/bibliographic/bd711.html
"""

import re

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.authority_standard_number import extract_identifier
from adapters.transformers.marc.common import (
    distinct,
    non_blank_subfields,
    non_empty,
)
from models.pipeline.concept import Concept, Contributor
from models.pipeline.id_label import Label
from models.pipeline.identifier import Identifiable
from utils.types import RawConceptType

logger = structlog.get_logger(__name__)

CONTRIBUTOR_TAGS = ["100", "110", "111", "700", "710", "711"]
# Keyed by the last two digits of the tag, which the main and added entry share
TAG_TO_CONCEPT_TYPE: dict[str, RawConceptType] = {
    "00": "Person",
    "10": "Organisation",
    "11": "Meeting",
}
LABEL_SUBFIELDS = {
    "00": ["a", "b", "c", "d", "t", "n", "p", "q", "l"],
    "10": ["a", "b", "c", "d", "t", "p", "q", "l"],
    "11": ["a", "c", "d", "t"],
}
# The relator term is ǂe on personal and corporate names and ǂj on meeting names.
# Personal names also take ǂj, for an attribution such as "Follower of"
ROLE_SUBFIELDS = {"00": ["e", "j"], "10": ["e"], "11": ["j"]}
TRAILING_COMMAS = re.compile(r"(\s*,)+\s*$")


def extract_contributors(record: Record) -> list[Contributor]:
    """Extract a contributor from each name field, primary ones first, deduplicated."""
    return distinct_contributors(
        non_empty(
            extract_contributor(field) for field in record.get_fields(*CONTRIBUTOR_TAGS)
        )
    )


def extract_contributor(field: Field) -> Contributor | None:
    kind = field.tag[1:]
    raw_label = " ".join(
        value.strip() for value in non_blank_subfields(field, *LABEL_SUBFIELDS[kind])
    )
    if not raw_label:
        logger.error("Name field without a label", tag=field.tag, field=str(field))
        return None

    concept_type = get_agent_type(field)
    label, identifier_label = agent_labels(raw_label, concept_type)
    # A name's ǂ0 holds an LC Names identifier; its second indicator names no scheme
    authority_id = extract_identifier(field, concept_type, scheme="lc-names")
    agent_id = authority_id or Identifiable.identifier_from_text(
        identifier_label, concept_type
    )
    roles = field.get_subfields(*ROLE_SUBFIELDS[kind])

    return Contributor(
        agent=Concept(id=agent_id, label=label, type=concept_type),
        roles=[Label(label=role.strip().removesuffix(".")) for role in roles],
        primary=field.tag.startswith("1"),
    )


def get_agent_type(field: Field) -> RawConceptType:
    """
    Return the type of agent a name field denotes.

    A personal name with a title of a work (ǂt) may not name a real person, so
    it is a plain Agent, as in the Scala pipeline.
    """
    if field.tag.endswith("00") and field.get_subfields("t"):
        return "Agent"
    return TAG_TO_CONCEPT_TYPE[field.tag[1:]]


def agent_labels(raw_label: str, concept_type: RawConceptType) -> tuple[str, str]:
    """
    Return the agent's label and the label its identifier is derived from.

    Trailing commas are trimmed from the label, except on an Agent. Like the
    Scala pipeline, a Person's identifier derives from the trimmed label and an
    Organisation's or Meeting's from the label as catalogued.
    """
    if concept_type == "Agent":
        return raw_label, raw_label
    label = TRAILING_COMMAS.sub("", raw_label)
    return label, label if concept_type == "Person" else raw_label


def distinct_contributors(contributors: list[Contributor]) -> list[Contributor]:
    """Remove repeated contributors and move primary ones to the front."""
    return distinct(
        sorted(contributors, key=lambda c: not c.primary),
        key=lambda c: (c.agent, c.roles),
    )
