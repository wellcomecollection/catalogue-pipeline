"""
Extracting contributors from the following fields:
Main Entry Personal, Corporate and Meeting names:
https://www.loc.gov/marc/bibliographic/bd100.html
https://www.loc.gov/marc/bibliographic/bd110.html
https://www.loc.gov/marc/bibliographic/bd111.html

Added Entry Personal, Corporate and Meeting names:
https://www.loc.gov/marc/bibliographic/bd700.html
https://www.loc.gov/marc/bibliographic/bd710.html
https://www.loc.gov/marc/bibliographic/bd711.html
"""

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.text_utils import (
    clean_concept_label,
    normalise_identifier_value,
)
from models.pipeline.concept import Concept, Contributor
from models.pipeline.id_label import Label
from models.pipeline.identifier import Id, Identifiable, SourceIdentifier
from utils.types import ConceptType


def extract_contributors(record: Record) -> list[Contributor]:
    return distinct_contributors(
        [
            format_field(field)
            for field in record.get_fields("100", "110", "111", "700", "710", "711")
        ]
    )


def distinct_contributors(contributors: list[Contributor]) -> list[Contributor]:
    """
    Filter duplicates from `contributors, fronting primary contributors, but otherwise preserving order.
    An entry is a duplicate if it matches apart from the value of primary.
    """
    # Primary contributors should go first
    sorted_contributors = sorted(contributors, key=lambda c: not c.primary)

    seen_contributors = set()
    deduplicated = []
    for contributor in sorted_contributors:
        # Use a normalised representation (with `primary=False`) for comparison
        contributor_key = contributor.model_copy(
            update={"primary": False}
        ).model_dump_json()
        if contributor_key not in seen_contributors:
            deduplicated.append(contributor)
            seen_contributors.add(contributor_key)

    return deduplicated


type_of_contributor: dict[str, ConceptType] = {
    "00": "Person",
    "10": "Organisation",
    "11": "Meeting",
}

label_subfields: dict[str, list[str]] = {
    "00": ["a", "b", "c", "d", "q"],
    "10": ["a", "b", "c", "d", "q"],
    "11": ["a", "c", "d", "n"],
}


def format_field(field: Field) -> Contributor:
    tag = field.tag
    contributor_type = type_of_contributor[tag[1:]]
    label = label_from_field(field, label_subfields[tag[1:]])
    concept_id = Identifiable.from_source_identifier(
        SourceIdentifier(
            value=normalise_identifier_value(label),
            ontology_type=contributor_type,
            identifier_type=Id(id="label-derived"),
        )
    )

    return Contributor(
        agent=Concept(
            label=label,
            type=contributor_type,
            id=concept_id,
        ),
        roles=roles(field),
        primary=is_primary(tag),
    )


def label_from_field(field: Field, subfields: list[str]) -> str:
    """Join selected subfields into a contributor label applying trailing punctuation trimming.

    We keep the existing selection of subfields (excluding work-related ones like $t,$n,$p,$l)
    and apply cleaning only to the final combined label so internal punctuation (e.g. commas/colons
    that convey structure) is preserved. This matches previous semantics while still trimming any
    trailing punctuation from the overall label.
    """
    parts = [v.strip() for v in field.get_subfields(*subfields) if v.strip()]
    combined = " ".join(parts)
    return clean_concept_label(combined)


def is_primary(tag: str) -> bool:
    return tag[0] == "1"


def roles(field: Field) -> list[Label]:
    """
    Extract a list of roles from instances of the Relator Term subfield

    This implementation is not strictly conformant with the MARC specification,
    but takes into account one of the vagaries of the EBSCO data.

    The Relator Term subfield is only $e on Personal and Corporate Name
    fields (x00 and x10), and is $j on Meeting fields (x11).

    However, on the two 711 records with what appears to be a relator field
    that value is found in subfield e (Subordinate Unit).

    If EBSCO fix this, then we will have to update accordingly.
    """
    return [
        Label(label=clean_concept_label(value.strip()))
        for value in field.get_subfields("e")
    ]
