import re

from pymarc.record import Record

from adapters.transformers.marc.common import get_a_subfields

# Sierra system number: b + 7 digits + check character (digit or 'x')
# Matches the Scala regex in IdentifierRegexes.scala
SIERRA_SYSTEM_NUMBER_RE = re.compile(r"^b[0-9]{7}[0-9x]$")

UUID_RE = re.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def extract_predecessor_id(record: Record) -> str | None:
    """Extract predecessor identifier MARC 907 $a."""
    pred_id = get_a_subfields("907", record)
    pred_id = list({val.lstrip(".") for val in pred_id})

    if len(pred_id) > 1:
        raise ValueError("Multiple distinct instances of varfield with tag 907")
    if not pred_id:
        return None

    return pred_id[0]


def extract_sierra_predecessor_id(record: Record) -> str | None:
    """Extract predecessor Sierra system number from MARC 907 $a."""
    identifier = extract_predecessor_id(record)
    if not identifier:
        return None

    if not SIERRA_SYSTEM_NUMBER_RE.match(identifier):
        raise ValueError(
            "Predecessor identifier does not match Sierra system number format"
        )

    return identifier


def extract_calm_predecessor_id(record: Record) -> str | None:
    """Extract predecessor CALM record ID from MARC 907 $a."""
    identifier = extract_predecessor_id(record)
    if not identifier:
        return None

    if not UUID_RE.match(identifier):
        raise ValueError("Predecessor identifier does not match CALM record ID format")

    return identifier
