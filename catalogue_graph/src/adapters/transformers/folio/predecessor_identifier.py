import re

import structlog
from pymarc.record import Record

from adapters.transformers.marc.common import get_a_subfields

logger = structlog.get_logger(__name__)

# Sierra system number: b + 7 digits + check character (digit or 'x')
# Matches the Scala regex in IdentifierRegexes.scala
SIERRA_SYSTEM_NUMBER_RE = re.compile(r"^b[0-9]{7}[0-9x]$")


def extract_predecessor_id(record: Record) -> str | None:
    """Extract predecessor Sierra system number from MARC 907 $a."""
    pred_id = list(set(get_a_subfields("907", record)))
    if len(pred_id) > 1:
        logger.warning(
            "Multiple distinct instances of varfield with tag 907",
            count=len(pred_id),
        )
        raise ValueError("Multiple distinct instances of varfield with tag 907")
    if not pred_id:
        return None

    identifier = pred_id[0].lstrip(".")
    if not SIERRA_SYSTEM_NUMBER_RE.match(identifier):
        logger.warning(
            "Predecessor identifier does not match Sierra system number format",
            value=identifier,
        )
        raise ValueError(
            "Predecessor identifier does not match Sierra system number format"
        )
    return identifier
