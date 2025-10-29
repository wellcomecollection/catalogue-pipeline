from __future__ import annotations

import logging

from models.pipeline.concept import Concept, Genre
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier
from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    build_subdivision_concepts,
)
from adapters.ebsco.transformers.text_utils import (
    normalise_identifier_value,
    normalise_label,
)

logger: logging.Logger = logging.getLogger(__name__)


def build_primary_concept(field: Field) -> Concept | None:
    primary = field.get_subfields("a")
    if len(primary) == 0:
        return None
    raw = primary[0]
    label = normalise_label(raw, "Genre")
    source_identifier = SourceIdentifier(
        identifier_type=Id(id="label-derived"),
        ontology_type="Genre",
        value=normalise_identifier_value(label),
    )
    return Concept(
        label=label,
        type="GenreConcept",
        id=Identifiable.from_source_identifier(source_identifier),
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
    if primary_concept is None or not label:
        return None

    genre_label = normalise_label(label, "Genre")
    concepts = [primary_concept] + build_subdivision_concepts(field)
    return Genre(label=genre_label, concepts=concepts)
