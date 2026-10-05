"""
Identifiers for 6xx and 655 headings from ǂ0, per the second indicator.
https://www.loc.gov/marc/bibliographic/bd655.html
"""

import re

import structlog
from pymarc.field import Field

from adapters.transformers.marc.common import non_empty
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier

logger = structlog.get_logger(__name__)

# Punctuation is stripped before these are matched, so the dots are gone.
URL_PREFIXES = (
    "http://idlocgov/authorities/subjects/",
    "https://idlocgov/authorities/subjects/",
    "http://idlocgov/authorities/names/",
    "https://idlocgov/authorities/names/",
    "http://idnlmnihgov/mesh/",
    "https://idnlmnihgov/mesh/",
    "(DNLM)",
)


def extract_identifier(field: Field, ontology_type: str) -> Identifiable | None:
    """
    Build an Identifiable from a heading's ǂ0.

    The second indicator names the scheme: 0 for Library of Congress, 2 for MeSH.
    Returns None if there is no ǂ0, if repeated ǂ0 values disagree, if the scheme
    is any other, or if an LoC identifier has an unrecognised prefix.
    """
    values = non_empty(
        list(dict.fromkeys(normalise_identifier(v) for v in field.get_subfields("0")))
    )
    if not values:
        return None
    if len(values) > 1:
        logger.warning("Multiple identifier subfields", tag=field.tag, field=str(field))
        return None
    identifier_type = _identifier_type(field, values[0])
    if identifier_type is None:
        return None
    return Identifiable.from_source_identifier(
        SourceIdentifier(
            identifier_type=Id(id=identifier_type),
            ontology_type=ontology_type,
            value=values[0],
        )
    )


def _identifier_type(field: Field, value: str) -> str | None:
    indicator2 = field.indicators.second if field.indicators else None
    if indicator2 == "0":
        return _loc_scheme(field, value)
    if indicator2 == "2":
        return "nlm-mesh"
    return None


def _loc_scheme(field: Field, value: str) -> str | None:
    # Indicator 0 covers both LCSH and LC Names. LCSH identifiers are always prefixed
    # with "sh". LC Names use several prefixes (n, no, nb, nr, ...), so any values
    # starting with "n" is accepted.
    prefix = re.split(r"\d", value, maxsplit=1)[0]
    if prefix == "sh":
        return "lc-subjects"
    if prefix.startswith("n"):
        return "lc-names"

    logger.error(
        "Could not determine LoC scheme from identifier", tag=field.tag, value=value
    )
    return None


def normalise_identifier(value: str) -> str:
    stripped = re.sub(r"[,.\s]", "", value)
    for prefix in URL_PREFIXES:
        stripped = stripped.removeprefix(prefix)
    return stripped
