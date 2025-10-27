from __future__ import annotations

import logging
from itertools import chain

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import (
    extract_concept_from_subfield_value,
    non_empty,
    subdivision_concepts,
)
from models.pipeline.concept import Genre
from utils.types import ConceptType

logger: logging.Logger = logging.getLogger(__name__)

SUBDIVISION_SUBFIELDS: list[str] = ["v", "x", "y", "z"]
LABEL_SUBFIELDS: list[str] = ["a"] + SUBDIVISION_SUBFIELDS

# Mapping of subdivision code -> SourceConcept.type override
CONCEPT_TYPE_MAP: dict[str, ConceptType] = {
    "y": "Period",
    "z": "Place",
    # 'a', 'v', 'x' default to 'Concept'
}


def extract_genres(record: Record) -> list[Genre]:
    """
    Build a list of Genre objects from MARC 655 fields.
    """
    return non_empty(extract_genre(field) for field in record.get_fields("655"))


def extract_genre(field: Field) -> Genre | None:
    """
    Create a Genre from a single 655 field or return None if invalid.
    """
    a_subfields = field.get_subfields("a")
    if len(a_subfields) == 0:
        return None
    if len(a_subfields) > 1:
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None
    subdivision_subfields = field.get_subfields("v", "x", "y", "z")
    genre_label = " ".join(chain(a_subfields, subdivision_subfields))

    concepts = [
        extract_concept_from_subfield_value(
            "a", a_subfields[0], default_ontology_type="Genre"
        )
    ] + subdivision_concepts(field, SUBDIVISION_SUBFIELDS)

    return Genre(
        label=genre_label,
        concepts=concepts,
    )
