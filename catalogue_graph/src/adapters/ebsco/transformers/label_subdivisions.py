from __future__ import annotations

import re
from collections.abc import Iterable
from itertools import chain

from pymarc.field import Field

from adapters.ebsco.transformers.parsers.period import (
    RE_4_DIGIT_DATE_RANGE,
    RE_NTH_CENTURY,
    parse_period,
)
from adapters.ebsco.transformers.text_utils import (
    normalise_label,
)
from models.pipeline.concept import Concept
from models.pipeline.identifier import Identifiable, Unidentifiable
from utils.types import RawConceptType

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
SUBFIELD_TYPE_MAP: dict[str, RawConceptType] = {"y": "Period", "z": "Place"}


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


def build_subdivision_concepts(field: Field) -> list[Concept]:
    """Return subdivision concepts (v,x → Concept; y → Period; z → Place).

    Identifier is always label-derived. Trailing punctuation trimmed via
    clean_concept_label. This is the shared subdivision builder used by
    specific ontology transformers (e.g. Genre, Subject).
    """
    concepts: list[Concept] = []
    for subfield in field.subfields:
        code = getattr(subfield, "code", "")
        if code not in SUBDIVISION_CODES:
            continue

        ontology_type = SUBFIELD_TYPE_MAP.get(code, "Concept")
        concepts.append(build_concept(subfield.value, ontology_type))

    return concepts


def should_create_range(label: str) -> bool:
    """
    The scala transformer doesn't create a range for all parseable periods in subdivisions
    So far, I have only seen them for:

     Centuries
     >>> should_create_range("19th century")
     True

     Ranges consisting of one or two four-digit years
     >>> should_create_range("1901")
     True
     >>> should_create_range("1904-")
     True
     >>> should_create_range("1601-1666")
     True

     But not ranges with three-digit years
     >>> should_create_range("501-1066")
     False

     Or ranges with other content
     >>> should_create_range("Siege of Bielefeld 1820-1856")
     False
    """
    return (
        RE_NTH_CENTURY.match(label) is not None
        or RE_4_DIGIT_DATE_RANGE.match(label) is not None
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

    if raw_type == "Period" and should_create_range(label):
        return parse_period(label, identifier=id)
    else:
        return Concept(
            id=id,
            label=label,
            type=raw_type,
        )


def get_concept_identifier(label: str, raw_type: RawConceptType) -> Identifiable:
    concept_type = Concept.type_to_display_type(raw_type)
    return Identifiable.identifier_from_text(label, concept_type)
