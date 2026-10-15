"""Shared helpers for building concepts from 6xx fields.

Each subdivision subfield becomes a concept whose type depends on the subfield code.
"""

from __future__ import annotations

import re

from adapters.transformers.marc.period import parse_period
from models.pipeline.concept import Concept
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

SUBDIVISION_CODES: list[str] = ["v", "x", "y", "z"]
SUBFIELD_TYPE_MAP: dict[str, RawConceptType] = {"y": "Period", "z": "Place"}

LEADING_ROMAN_NUMERAL = r'^"?(?=[mdclxvi.,\s]{3,})m*[.,]?\s?(c[md]|d?c*)[.,]?\s?(x[cl]|l?x*)[.,]?\s?(i[xv]|v?i*)\b'
PERIOD_ID_NOISE = re.compile(
    "|".join(
        [
            r"\[gaps\]",
            "floruit",
            r"fl\.",
            r"\bfl\b",
            "between",
            r'[()\[\]?."©]',
            LEADING_ROMAN_NUMERAL,
        ]
    )
)


def normalise_period_id_label(label: str) -> str:
    # Match Scala pipeline preprocessing (PeriodParser.preprocess) so that label-derived
    # concept identifiers agree across pipelines. One deliberate divergence: "fl" is only
    # stripped as a whole word, otherwise "Influenza" would become "inuenza".
    return PERIOD_ID_NOISE.sub("", label.lower()).strip()


def build_concept(
    label: str,
    concept_type: RawConceptType,
    given_identifier: Identifiable | Unidentifiable | None = None,
) -> Concept:
    """Build a concept from a label. Without an identifier, one is derived from the label."""
    identifier = given_identifier or label_derived_identifier(label, concept_type)
    if concept_type == "Period":
        return parse_period(label, identifier=identifier)
    return Concept(id=identifier, label=label, type=concept_type)


def label_derived_identifier(label: str, concept_type: RawConceptType) -> Identifiable:
    """Derive an identifier from a concept's label, for concepts with no authority identifier."""
    if concept_type == "Period":
        label = normalise_period_id_label(label) or label
    return Identifiable.identifier_from_text(
        label, Concept.type_to_display_type(concept_type)
    )
