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

from collections import OrderedDict
from itertools import chain

from pymarc.field import Field
from pymarc.record import Record

from models.work import Concept, ConceptType, Contributor, SourceIdentifier


def extract_contributors(record: Record) -> list[Contributor]:
    return distinct_contributors(
        [
            format_field(field)
            for field in record.get_fields("100", "110", "111", "700", "710", "711")
        ]
    )


def distinct_contributors(contributors: list[Contributor]) -> list[Contributor]:
    """
    Filter duplicates from `contributors, fronting primary contributors,
    but otherwise preserving order
    An entry is a duplicate if it matches apart from the value of primary
    """
    # First, create a list of 2-tuples containing all the values
    # primary is arbitrarily set to false in the KEY only,
    # so that both duplicates share the same key.
    # This is just to make the creation of the two dicts below
    # less verbose
    all_contributors = [
        (
            str(contributor.model_copy(update={"primary": False}).model_dump()),
            contributor,
        )
        for contributor in contributors
    ]
    # The goal is to have all primaries, then all non-primaries
    # that are not primary, so we split them.
    # Note: Not itertools.groupby, because we want to preserve input order
    primary_contributors = {k: v for (k, v) in all_contributors if v.primary}
    secondary_contributors = [
        (k, v)
        for (k, v) in all_contributors
        if not v.primary and k not in primary_contributors
    ]
    # Now we have all the
    as_ordered_dict = OrderedDict(
        chain(primary_contributors.items(), secondary_contributors)
    )
    return list(as_ordered_dict.values())


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
    return Contributor(
        agent=Concept(
            label=label,
            type=contributor_type,
            id=SourceIdentifier(
                value=label,
                ontology_type=contributor_type,
                identifier_type="label-derived",
            ),
        ),
        roles=roles(field),
        primary=is_primary(tag),
    )


def label_from_field(field: Field, subfields: list[str]) -> str:
    return " ".join(field.get_subfields(*subfields))


def is_primary(tag: str) -> bool:
    return tag[0] == "1"


def roles(field: Field) -> list[str]:
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
    return [value.strip() for value in field.get_subfields("e")]
