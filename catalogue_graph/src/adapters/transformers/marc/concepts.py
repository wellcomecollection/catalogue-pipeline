"""Shared helpers for building concepts from 6xx fields.

Each subdivision subfield becomes a concept whose type depends on the subfield code.
"""

from __future__ import annotations

import re

from adapters.transformers.marc.period import parse_period
from adapters.transformers.utils.text_utils import (
    normalise_label,
)
from models.pipeline.concept import Concept
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

SUBDIVISION_CODES: list[str] = ["v", "x", "y", "z"]
SUBFIELD_TYPE_MAP: dict[str, RawConceptType] = {"y": "Period", "z": "Place"}


# Match Scala pipeline preprocessing (PeriodParser.preprocess) so that label-derived
# concept identifiers agree across pipelines. One deliberate divergence: A bare "fl"
# is left alone, otherwise a word like "Influenza" would become "inuenza".
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
    """
    Strip punctuation, qualifiers and a leading roman numeral from a Period label, so
    that renderings of the same period share an id.
    >>> normalise_period_id_label("MDCCLXXXVII. [1787]")
    '1787'
    >>> normalise_period_id_label("1851 Nov. 27.")
    '1851 nov 27'
    >>> normalise_period_id_label("To 1763 (New France)")
    'to 1763 new france'
    >>> normalise_period_id_label("fl. 1620-1650")
    '1620-1650'
    >>> normalise_period_id_label("Influenza Epidemic, 1918-1919.")
    'influenza epidemic, 1918-1919'
    """
    return PERIOD_ID_NOISE.sub("", label.lower()).strip()


def type_specific_id_normalisation(label: str, ontology_type: str) -> str | None:
    if ontology_type == "Organisation":
        return label
    if ontology_type == "Period":
        return normalise_period_id_label(label)
    return None


def build_concept(
    raw_label: str,
    raw_type: RawConceptType,
    preserve_trailing_period: bool = False,
    is_identifiable: bool = True,
    identifier: Identifiable | None = None,
) -> Concept:
    label = normalise_label(raw_label, raw_type, preserve_trailing_period)
    # Organisations use the raw label to create a Label Derived Identifier.
    # (erroneously - this is maintained for fidelity with the Scala transformer)
    # Label Derived Identifiers call getLabel, in order to pull out the text for the id
    # https://github.com/wellcomecollection/catalogue-pipeline/blob/6c5ee0e90eda680e82a2c2716a4f31e6eb4a96ea/pipeline/transformer/transformer_marc_common/src/main/scala/weco/pipeline/transformer/marc_common/transformers/MarcHasRecordControlNumber.scala#L178
    # In the case of an Organisation, this falls back to AbstractAgent.getLabel, which
    # simply joins the label fields with a space
    # https://github.com/wellcomecollection/catalogue-pipeline/blob/6c5ee0e90eda680e82a2c2716a4f31e6eb4a96ea/pipeline/transformer/transformer_marc_common/src/main/scala/weco/pipeline/transformer/marc_common/transformers/MarcAbstractAgent.scala#L24
    # This is in contrast with other concepts (e.g. Person, below), which also performs the normalisation
    # in the same fashion as normalise_label does here.
    # https://github.com/wellcomecollection/catalogue-pipeline/blob/6c5ee0e90eda680e82a2c2716a4f31e6eb4a96ea/pipeline/transformer/transformer_marc_common/src/main/scala/weco/pipeline/transformer/marc_common/transformers/MarcPerson.scala#L23
    label_for_id = type_specific_id_normalisation(raw_label, raw_type) or label

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
