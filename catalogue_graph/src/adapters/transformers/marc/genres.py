"""
Extract genres from MARC 655 - Index Term-Genre/Form
https://www.loc.gov/marc/bibliographic/bd655.html
"""

from __future__ import annotations

from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.ebsco.authority_standard_number import extract_identifier
from adapters.transformers.marc.common import non_empty, non_repeatable_subfield
from adapters.transformers.marc.concepts import (
    SUBDIVISION_CODES,
    SUBFIELD_TYPE_MAP,
    build_concept,
)
from adapters.transformers.utils.text_utils import normalise_label
from models.pipeline.concept import Concept, Genre


def extract_genres(record: Record) -> list[Genre]:
    """One Genre per 655, deduplicated on label; the first occurrence wins."""
    return distinct(
        non_empty(extract_genre(field) for field in record.get_fields("655"))
    )


def distinct(genres: list[Genre]) -> list[Genre]:
    seen = set()
    result = []
    for genre in genres:
        if genre.label not in seen:
            seen.add(genre.label)
            result.append(genre)
    return result


def extract_genre(field: Field) -> Genre | None:
    """Genre from one 655, or None without a ǂa. A repeated ǂa is logged and only the first used."""
    primary = non_repeatable_subfield(field, "a")
    if primary is None:
        return None

    return Genre(
        label=normalise_label(build_label(primary, field), "GenreConcept"),
        concepts=[
            build_concept(
                primary, "GenreConcept", identifier=extract_identifier(field, "Genre")
            ),
            *build_subdivision_concepts(field),
        ],
    )


def build_label(primary: str, field: Field) -> str:
    """ǂa, then the subdivisions in document order, joined with ' - '."""
    subdivisions = (value.strip() for value in field.get_subfields(*SUBDIVISION_CODES))
    return " - ".join([primary, *subdivisions])


def build_subdivision_concepts(field: Field) -> list[Concept]:
    """One label-derived concept per subdivision subfield, in document order."""
    return [
        build_concept(subfield.value, SUBFIELD_TYPE_MAP.get(subfield.code, "Concept"))
        for subfield in field.subfields
        if subfield.code in SUBDIVISION_CODES
    ]
