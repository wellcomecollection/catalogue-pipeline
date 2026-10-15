"""
Identifiers from a field's ǂ0, for subject and genre headings and for names.
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
# Library of Congress identifiers start with a scheme prefix: "sh" for LCSH, and
# one of several starting with "n" (n, no, nb, nr, ...) for LC Names
LOC_PREFIXES = {"lc-subjects": "sh", "lc-names": "n[a-z]*"}
# The schemes the second indicator of a subject or genre heading allows
INDICATED_SCHEMES = {"0": ["lc-subjects", "lc-names"], "2": ["nlm-mesh"]}


def extract_identifier(
    field: Field, ontology_type: str, scheme: str | None = None
) -> Identifiable | None:
    """
    Build an Identifiable from a field's ǂ0.

    The scheme is the one given or, on a subject or genre heading, the one the
    second indicator names: 0 for Library of Congress, 2 for MeSH. Name fields
    give it, as their second indicator says nothing about the scheme. Returns
    None if there is no ǂ0, if repeated ǂ0 values disagree, if the scheme is any
    other, or if a Library of Congress identifier lacks its scheme's prefix.
    """
    values = non_empty(
        list(dict.fromkeys(normalise_identifier(v) for v in field.get_subfields("0")))
    )
    if not values:
        return None
    if len(values) > 1:
        logger.warning("Multiple identifier subfields", tag=field.tag, field=str(field))
        return None
    value = values[0]

    indicator2 = field.indicators.second if field.indicators else ""
    candidates = [scheme] if scheme else INDICATED_SCHEMES.get(indicator2, [])
    scheme = next((s for s in candidates if _has_scheme_prefix(value, s)), None)
    if scheme is None:
        if candidates:
            logger.error(
                "Could not determine LoC scheme from identifier",
                tag=field.tag,
                value=value,
            )
        return None
    return Identifiable.from_source_identifier(
        SourceIdentifier(
            identifier_type=Id(id=scheme), ontology_type=ontology_type, value=value
        )
    )


def _has_scheme_prefix(value: str, scheme: str) -> bool:
    """True if the identifier has the scheme's prefix. Only the LoC schemes have one."""
    if scheme not in LOC_PREFIXES:
        return True
    prefix = re.split(r"\d", value, maxsplit=1)[0]
    return re.fullmatch(LOC_PREFIXES[scheme], prefix) is not None


def normalise_identifier(value: str) -> str:
    stripped = re.sub(r"[,.\s]", "", value)
    for prefix in URL_PREFIXES:
        stripped = stripped.removeprefix(prefix)
    return stripped
