"""Shared helpers for building concepts from 6xx fields.

Each subdivision subfield becomes a concept whose type depends on the subfield code.
"""

from __future__ import annotations

import re

from adapters.transformers.marc.parsers.period import (
    RE_4_DIGIT_DATE_RANGE,
    RE_NTH_CENTURY,
    parse_period,
)
from adapters.transformers.utils.text_utils import (
    normalise_label,
)
from models.pipeline.concept import Concept, Period
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

SUBDIVISION_CODES: list[str] = ["v", "x", "y", "z"]
SUBFIELD_TYPE_MAP: dict[str, RawConceptType] = {"y": "Period", "z": "Place"}


def should_create_range(label: str) -> bool:
    """
    Whether a Period label is one the Python parser handles: a century or a
    range of four-digit years. This is a deliberate subset of the Scala
    PeriodParser grammar, which also handles exact dates, decades, seasons,
    qualifiers such as "early" or "ca.", BC years and half-bounded ranges.
    Labels outside the subset get no range rather than a wrong one.

     >>> should_create_range("19th century")
     True
     >>> should_create_range("18th cent.")
     True
     >>> should_create_range("1901")
     True
     >>> should_create_range("1904-")
     True
     >>> should_create_range("1601-1666")
     True

     The whole label must match, so a date with a day or a parenthetical
     qualifier is left alone:
     >>> should_create_range("1851 Nov. 27")
     False
     >>> should_create_range("1714-1727 (George Ier)")
     False
     >>> should_create_range("501-1066")
     False
     >>> should_create_range("Siege of Bielefeld 1820-1856")
     False
    """
    return (
        RE_NTH_CENTURY.fullmatch(label) is not None
        or RE_4_DIGIT_DATE_RANGE.fullmatch(label) is not None
    )


def normalise_period_id_label(label: str) -> str:
    """
    Period id values are preprocessed for standard normalisation
    by removing the dots from certain datetime abbreviations.
    A.D. and B.C become ad and bc
    >>> normalise_period_id_label("2000 A.D.")
    '2000 ad'
    >>> normalise_period_id_label("One Million Years B.C.")
    'One Million Years bc'

    ca. becomes ca
    >>> normalise_period_id_label("ca. 1066")
    'ca 1066'
    >>> normalise_period_id_label("teatime, ca. 1066")
    'teatime, ca 1066'

    Each of these substitutions is subject to constraints,
    A.D. and B.C. are only replaced when preceded by a space
    >>> normalise_period_id_label("N.O.R.A.D. Santa Tracker")
    'N.O.R.A.D. Santa Tracker'

    ca. is only replaced either at the beginning of the label,
    or when preceded by a space
    >>> normalise_period_id_label("Monica.")
    'Monica.'
    """
    return re.sub(
        r"((?<=^)|(?<=\s))ca\.",
        "ca",
        label.replace(" A.D.", " ad").replace(" B.C.", " bc"),
    )


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
        if should_create_range(label):
            return parse_period(label, identifier=id)
        return Period(id=id, label=label)
    return Concept(id=id, label=label, type=raw_type)


def get_concept_identifier(label: str, raw_type: RawConceptType) -> Identifiable:
    concept_type = Concept.type_to_display_type(raw_type)
    return Identifiable.identifier_from_text(label, concept_type)
