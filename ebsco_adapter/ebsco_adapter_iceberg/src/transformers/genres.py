import logging
from typing import List, Tuple

from pymarc.record import Record
from pymarc.field import Field

from models.work import Genre, SourceIdentifier, SourceConcept

logger = logging.getLogger(__name__)

# Subfields that contribute to the genre label (order & inclusion)
# Ordering here enforces $a first in both label & concept list regardless of
# its physical position in the field.
LABEL_SUBFIELDS = ["a", "v", "x", "y", "z"]

# Mapping of subdivision code -> SourceConcept.type override
CONCEPT_TYPE_MAP = {
    "y": "Period",
    "z": "Place",
    # 'a', 'v', 'x' default to 'Concept'
}


def extract_genres(record: Record) -> List[Genre]:
    """
    Build a list of Genre objects from MARC 655 fields.

    Rules (augmented for new scenarios):
      - A genre is produced for each 655 field that has exactly one (non-empty) $a.
      - If a 655 field has >1 $a, discard the entire field and log:
            "Repeated Non-repeating field $a found in 655 field"
      - Subfields a,v,x,y,z (if present, non-empty) form:
            * The genre label: concatenated in canonical order a v x y z separated by single spaces,
              regardless of their order in the MARC field.
            * A concept per participating subfield value, in the same canonical order.
      - $a always appears first in the label & concept list even if it occurs later in the raw data.
      - Concept labels have trailing punctuation . , ; : stripped (scenario expects stripping for e.g. "Dublin.")
        but the overall genre label preserves original punctuation.
      - Concept.type is derived:
            y -> Period
            z -> Place
            else -> Concept
      - Each concept gets a SourceIdentifier:
            identifier_type = "label-derived"
            ontology_type   = "Genre"   (kept uniform; existing scenarios assert this for simple case)
            value           = normalised concept label (lowercase, collapsed internal spaces)
      - Genre.id remains None (not asserted yet).
      - Genre.type set to "Genre".
    """
    genres: List[Genre] = []

    for field in record.get_fields("655"):
        genre = _build_genre_from_field(field)
        if genre is not None:
            genres.append(genre)

    return genres


# ----------------- Helpers ----------------- #


def _build_genre_from_field(field: Field) -> Genre | None:
    # Gather non-empty $a values
    a_values = [v.strip() for v in field.get_subfields("a") if v and v.strip()]

    if len(a_values) == 0:
        return None

    if len(a_values) > 1:
        logger.error("Repeated Non-repeating field $a found in 655 field")
        return None

    # Collect (code, raw_value) pairs in canonical order ignoring actual sequence in field
    ordered_pairs: list[Tuple[str, str]] = []
    for code in LABEL_SUBFIELDS:
        raw_values = [v for v in field.get_subfields(code) if v and v.strip()]
        for raw in raw_values:
            ordered_pairs.append((code, raw.strip()))

    if not ordered_pairs:
        return None

    # Build genre label preserving raw punctuation
    genre_label = " ".join(v for _, v in ordered_pairs)

    concepts: list[SourceConcept] = []
    for code, raw_value in ordered_pairs:
        concept_label = _clean_concept_label(raw_value)
        identifier = SourceIdentifier(
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
