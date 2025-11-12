from __future__ import annotations

import logging

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.authority_standard_number import extract_identifier
from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    build_concept,
    build_label_with_subdivisions,
    build_subdivision_concepts,
)
from adapters.ebsco.transformers.text_utils import (
    normalise_label,
)
from models.pipeline.concept import Concept, Genre

logger: logging.Logger = logging.getLogger(__name__)


def build_primary_concept(field: Field) -> Concept | None:
    primary = field.get_subfields("a")
    if len(primary) == 0:
        return None

    return build_concept(
        primary[0], "GenreConcept", identifier=extract_identifier(field, "Genre")
    )


def extract_genres(record: Record) -> list[Genre]:
    """
    Build a list of Genre objects from MARC 655 fields.
    """
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
    """
    Create a Genre from a single 655 field or return None if invalid.
    """
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0:
        return None
    if len(a_subfields) > 1:
        # Keep parity with existing behaviour: log and discard whole field
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None

    # Build hyphen-separated label consistent with Scala implementation.
    label = build_label_with_subdivisions(field)
    # Build concepts locally (primary + subdivisions); keep primary type logic
    # in this ontology-specific module rather than shared helpers.
    primary_concept = build_primary_concept(field)
    if primary_concept is None or not label:
        return None

    return Genre(
        label=normalise_label(label, "GenreConcept"),
        concepts=[primary_concept] + build_subdivision_concepts(field),
    )
