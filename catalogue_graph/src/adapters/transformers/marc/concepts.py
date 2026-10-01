"""Shared helpers for building concepts from 6xx fields.

Each subdivision subfield becomes a concept whose type depends on the subfield code.
"""

from __future__ import annotations

import re

from models.pipeline.concept import Concept
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

from adapters.transformers.marc.period import parse_period
from adapters.transformers.utils.text_utils import (
    normalise_label,
)

SUBDIVISION_CODES: list[str] = ["v", "x", "y", "z"]
SUBFIELD_TYPE_MAP: dict[str, RawConceptType] = {"y": "Period", "z": "Place"}

LEADING_ROMAN_NUMERAL = r'^"?(?=[mdclxvi.,\s]{3,})m*[.,]?\s?(c[md]|d?c*)[.,]?\s?(x[cl]|l?x*)[.,]?\s?(i[xv]|v?i*)\b'
PERIOD_ID_NOISE = re.compile(
    "|".join(
        [
            r"\[gaps\]",
            "floruit",
            r"fl\.",
            "between",
            r'[()\[\]?."©]',
            LEADING_ROMAN_NUMERAL,
        ]
    )
)


def normalise_period_id_label(label: str) -> str:
    # Match Scala pipeline preprocessing (PeriodParser.preprocess) so that label-derived
    # concept identifiers agree across pipelines. One deliberate divergence: A bare "fl"
    # is left alone, otherwise a word like "Influenza" would become "inuenza".
    return PERIOD_ID_NOISE.sub("", label.lower()).strip()


def label_for_identifier(raw_label: str, label: str, ontology_type: str) -> str:
    """The text a concept's label-derived identifier is built from."""
    if ontology_type == "Organisation":
        # Match the Scala pipeline, which derives organisation identifiers from the label as
        # catalogued without normalisation. This is an oversight, but normalising here would
        # re-mint organisation canonical ids.
        return raw_label
    if ontology_type == "Period":
        return normalise_period_id_label(raw_label)
    return label


def build_concept(
    raw_label: str,
    raw_type: RawConceptType,
    preserve_trailing_period: bool = False,
    is_identifiable: bool = True,
    identifier: Identifiable | None = None,
) -> Concept:
    label = normalise_label(raw_label, raw_type, preserve_trailing_period)
    label_for_id = label_for_identifier(raw_label, label, raw_type)

    id = identifier or (
        get_concept_identifier(label_for_id, raw_type)
        if is_identifiable
        else Unidentifiable()
    )

    if raw_type == "Period":
        return parse_period(label, identifier=id)
    return Concept(id=id, label=label, type=raw_type)


def get_concept_identifier(label: str, raw_type: RawConceptType) -> Identifiable:
    concept_type = Concept.type_to_display_type(raw_type)
    return Identifiable.identifier_from_text(label, concept_type)
