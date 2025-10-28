from __future__ import annotations

import logging

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import non_empty
from adapters.ebsco.transformers.label_subdivisions import (
    build_label_with_subdivisions,
    primary_and_subdivision_concepts,
)
from models.pipeline.concept import Genre

logger: logging.Logger = logging.getLogger(__name__)

SUBDIVISION_SUBFIELDS: list[str] = ["v", "x", "y", "z"]  # retained for callers/tests


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
    concepts = primary_and_subdivision_concepts(field, primary_type="Genre")
    if not label:
        return None
    return Genre(label=label, concepts=concepts)
