from __future__ import annotations

import logging
from itertools import chain
from pymarc.field import Field
from pymarc.record import Record, Subfield

from models.work import ConceptType, Genre, SourceConcept, SourceIdentifier

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
    return [genre for genre in (extract_genre(field) for field in record.get_fields("655")) if
            genre is not None]


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

    concepts = [_extract_concept_from_subfield_value("a", a_subfields[0])] + [
        _extract_concept_from_subfield(subfield) for subfield in field.subfields if
        subfield.code in SUBDIVISION_SUBFIELDS
    ]

    return Genre(
        id=None,
        label=genre_label,
        type="Genre",
        concepts=concepts,
    )


def _extract_concept_from_subfield(subfield: Subfield) -> SourceConcept:
    return _extract_concept_from_subfield_value(subfield.code, subfield.value)


def _extract_concept_from_subfield_value(code: str, value: str) -> SourceConcept:
    concept_label = _clean_concept_label(value)
    identifier = SourceIdentifier(
        identifier_type="label-derived",
        ontology_type="Genre",
        value=_normalise_identifier_value(concept_label),
    )
    return SourceConcept(
        id=identifier,
        label=concept_label,
        type=CONCEPT_TYPE_MAP.get(code, "Concept"),
    )


def _clean_concept_label(value: str) -> str:
    """
    Remove trailing punctuation (.,;:) and trim whitespace for concept labels.
    """
    return value.rstrip(".,;:").strip()


def _normalise_identifier_value(label: str) -> str:
    """
    Lowercase & collapse internal whitespace for identifier values.
    """
    return " ".join(label.split()).lower()
