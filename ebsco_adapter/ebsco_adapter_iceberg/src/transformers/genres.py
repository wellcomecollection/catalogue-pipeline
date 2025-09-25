from __future__ import annotations

import logging

from pymarc.field import Field
from pymarc.record import Record

from models.work import ConceptType, Genre, SourceConcept, SourceIdentifier

logger: logging.Logger = logging.getLogger(__name__)

# Subfields that contribute to the genre label (order & inclusion)
# Ordering here enforces $a first in both label & concept list regardless of
# its physical position in the field.
LABEL_SUBFIELDS: list[str] = ["a", "v", "x", "y", "z"]

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
    genres: list[Genre] = []

    for field in record.get_fields("655"):
        genre: Genre | None = _build_genre_from_field(field)
        if genre is not None:
            genres.append(genre)

    return genres


# ----------------- Helpers ----------------- #


def _build_genre_from_field(field: Field) -> Genre | None:
    """
    Construct a Genre from a single 655 field, or return None if the field is
    invalid (e.g., missing/duplicate $a) per the business rules.
    """
    a_values: list[str] = [
        v.strip() for v in field.get_subfields("a") if v and v.strip()
    ]

    if len(a_values) == 0:
        return None

    if len(a_values) > 1:
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None

    # Collect (code, raw_value) pairs in canonical order ignoring actual sequence in field
    ordered_pairs: list[tuple[str, str]] = []
    for code in LABEL_SUBFIELDS:
        raw_values: list[str] = [
            v for v in field.get_subfields(code) if v and v.strip()
        ]
        for raw in raw_values:
            ordered_pairs.append((code, raw.strip()))

    if not ordered_pairs:
        return None

    # Build genre label preserving raw punctuation
    genre_label: str = " ".join(v for _, v in ordered_pairs)

    concepts: list[SourceConcept] = []
    for code, raw_value in ordered_pairs:
        concept_label: str = _clean_concept_label(raw_value)
        identifier: SourceIdentifier = SourceIdentifier(
            identifier_type="label-derived",
            ontology_type="Genre",
            value=_normalise_identifier_value(concept_label),
        )
        concept_type = CONCEPT_TYPE_MAP.get(code, "Concept")
        concepts.append(
            SourceConcept(
                id=identifier,
                label=concept_label,
                type=concept_type,  # "Concept" / "Period" / "Place"
            )
        )

    return Genre(
        id=None,
        label=genre_label,
        type="Genre",
        concepts=concepts,
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
