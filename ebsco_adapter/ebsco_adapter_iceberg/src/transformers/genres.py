import logging
from typing import List

from pymarc.record import Record
from pymarc.field import Field

from models.work import Genre, SourceIdentifier, SourceConcept

logger = logging.getLogger(__name__)

# Subfields that contribute to the genre label (order & inclusion)
LABEL_SUBFIELDS = ["a", "v", "x", "y", "z"]

# Subfields that become individual concept entries (same list here)
CONCEPT_SUBFIELDS = LABEL_SUBFIELDS


def extract_genres(record: Record) -> List[Genre]:
    """
    Build a list of Genre objects from MARC 655 fields.

    Rules from feature spec:
      - A genre is produced for each 655 field that has exactly one (non-empty) $a.
      - If a 655 field has more than one $a subfield, the entire field is discarded
        and an error is logged: "Repeated Non-repeating field $a found in 655 field".
      - Additional subdivision subfields v, x, y, z (when present) are appended to
        the overall genre label in the sequence a, v, x, y, z, separated by single spaces.
      - Each of (a, v, x, y, z) that is present yields its own concept entry in the same order.
      - Concepts' labels have trailing punctuation . , ; : stripped (e.g. "Dublin." -> "Dublin"),
        but the overall genre label preserves the original values (including punctuation).
      - Each concept gets a SourceIdentifier:
            identifier_type = "label-derived"
            ontology_type   = "Genre"
            value           = normalised concept label (lowercase, collapsed internal spaces)
      - The Genre object:
            label    = constructed label
            concepts = list of concept objects
            id       = None (not specified in current scenarios)
            type     = "Genre"
    """
    genres: List[Genre] = []

    for field in record.get_fields("655"):
        genre = _build_genre_from_field(field)
        if genre is not None:
            genres.append(genre)

    return genres


# ----------------- Helpers ----------------- #


def _build_genre_from_field(field: Field) -> Genre | None:
    a_values = [v.strip() for v in field.get_subfields("a") if v and v.strip()]

    if len(a_values) == 0:
        # No primary 'a' term -> no genre
        return None

    if len(a_values) > 1:
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None

    # Collect all subfield values in the order defined by LABEL_SUBFIELDS
    label_parts: list[str] = []
    concept_values: list[str] = []

    for code in LABEL_SUBFIELDS:
        subs = [v for v in field.get_subfields(code) if v and v.strip()]
        if not subs:
            continue
        for raw in subs:
            stripped = raw.strip()
            # Add raw (unmodified) to overall label parts
            if code in CONCEPT_SUBFIELDS:
                label_parts.append(stripped)
                concept_values.append(stripped)

    if not label_parts:
        # Should not happen if there's a single $a, but guard anyway.
        return None

    genre_label = " ".join(label_parts)

    concepts: list[SourceConcept] = []
    for raw_value in concept_values:
        concept_label = _clean_concept_label(raw_value)
        identifier = SourceIdentifier(
            identifier_type="label-derived",
            ontology_type="Genre",
            value=_normalise_identifier_value(concept_label),
        )
        concept = SourceConcept(
            id=identifier,
            label=concept_label,
            type="Concept",  # Leave as "Concept" unless domain wants these to be "Genre"
        )
        concepts.append(concept)

    genre = Genre(
        id=None,
        label=genre_label,
        type="Genre",
        concepts=concepts,
    )
    return genre


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
