from __future__ import annotations

import logging

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    build_subdivision_concepts,
)
from adapters.ebsco.transformers.text_utils import (
    normalise_label,
)
from models.pipeline.concept import Concept, Genre
from models.pipeline.identifier import Identifiable

logger: logging.Logger = logging.getLogger(__name__)


def build_primary_concept(field: Field) -> Concept | None:
    primary = field.get_subfields("a")
    if len(primary) == 0:
        return None
    raw = primary[0]
    label = normalise_label(raw, "Genre")
    return Concept(
        label=label,
        type="Genre",
        id=Identifiable.identifier_from_text(label, "Genre"),
    )


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
        # Keep parity with existing behaviour: log and discard whole field
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None

    # Build hyphen-separated label consistent with Scala implementation.
    label = build_label_with_subdivisions(field)
    # Build concepts locally (primary + subdivisions); keep primary type logic
    # in this ontology-specific module rather than shared helpers.
    primary_concept = build_primary_concept(field)
    if primary_concept is None:
        return None
    # Subdivision concepts are generic Concept/Period/Place types – they will be normalised inside builder.
    concepts = [primary_concept] + build_subdivision_concepts(field)
    if not label:
        return None
    # Final label may include subdivisions joined earlier; normalise again for Genre semantics.
    genre_label = normalise_label(label, "Genre")
    return Genre(label=genre_label, concepts=concepts)
