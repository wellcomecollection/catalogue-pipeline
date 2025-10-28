from __future__ import annotations

from collections.abc import Iterable
from itertools import chain

from pymarc.field import Field

from adapters.ebsco.transformers.text_utils import (
    clean_concept_label,
    normalise_identifier_value,
)
from models.pipeline.concept import Concept
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier
from utils.types import ConceptType

"""Helpers for MARC label + subdivision handling (e.g. subjects, genres).

Scala implementation (`MarcCommonLabelSubdivisions`) builds labels by:
  label = $a followed by other subdivision subfields joined with " - "
and trims trailing period.

Differences adopted here per project guidance:
  - We keep broader trailing punctuation trimming on individual concept labels
    (.,;:) via `clean_concept_label`.
  - The joined label only trims a final period (matching original Scala intent)
    but we reuse the cleaned subfield content, so punctuation beyond period is
    already removed from concept labels.

Subdivision code → concept type mapping mirrors Scala:
  v,x → Concept; y → Period; z → Place.

Primary concept type is provided by caller (e.g. Genre, Person, etc.).
"""

SUBDIVISION_CODES: list[str] = ["v", "x", "y", "z"]
LABEL_SUBFIELD_CODES: list[str] = ["a"] + SUBDIVISION_CODES
SUBFIELD_TYPE_MAP: dict[str, ConceptType] = {"y": "Period", "z": "Place"}


def _field_subfields(field: Field, codes: Iterable[str]) -> list[str]:
    return field.get_subfields(*codes)


def build_label_with_subdivisions(field: Field) -> str:
    """Construct overall label: $a plus subdivision subfields joined by ' - '.

    Mirrors Scala MarcCommonLabelSubdivisions#getLabel logic except we apply
    broader per-subfield punctuation trimming earlier (see text_utils).
    The full label only has a trailing period trimmed to match Scala behaviour.
    """
    primary = _field_subfields(field, ["a"])  # we allow single $a
    subdivisions = _field_subfields(field, SUBDIVISION_CODES)
    ordered = list(chain(primary, subdivisions))
    label = " - ".join(s.strip() for s in ordered)
    # Trim only trailing period from full label (Scala behaviour)
    return label.rstrip(".")


def build_primary_concept(
    field: Field,
    ontology_type: ConceptType,
    default_ontology_type: ConceptType = "Concept",
) -> Concept | None:
    primary = _field_subfields(field, ["a"])
    if len(primary) == 0:
        return None
    # If repeated $a - log or ignore? Scala rejects multiple; we ignore extras here.
    value = primary[0]
    label = clean_concept_label(value)
    final_type: ConceptType = ontology_type if ontology_type else default_ontology_type
    source_identifier = SourceIdentifier(
        identifier_type=Id(id="label-derived"),
        ontology_type=final_type,
        value=normalise_identifier_value(label),
    )
    return Concept(
        id=Identifiable.from_source_identifier(source_identifier),
        label=label,
        type=final_type,
    )


def build_subdivision_concepts(field: Field) -> list[Concept]:
    concepts: list[Concept] = []
    for subfield in field.subfields:
        code = getattr(subfield, "code", "")
        if code in SUBDIVISION_CODES:
            raw = subfield.value
            label = clean_concept_label(raw)
            ontology_type = SUBFIELD_TYPE_MAP.get(code, "Concept")
            source_identifier = SourceIdentifier(
                identifier_type=Id(id="label-derived"),
                ontology_type=ontology_type,
                value=normalise_identifier_value(label),
            )
            concepts.append(
                Concept(
                    id=Identifiable.from_source_identifier(source_identifier),
                    label=label,
                    type=ontology_type,
                )
            )
    return concepts


def primary_and_subdivision_concepts(
    field: Field, primary_type: ConceptType
) -> list[Concept]:
    primary = build_primary_concept(field, primary_type)
    if primary is None:
        return []
    return [primary] + build_subdivision_concepts(field)
