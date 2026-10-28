import re
from collections.abc import Iterable

from pymarc.record import Record

from adapters.transformers.marc.common import get_a_subfields

# Sierra system number: b + 7 digits + check character (digit or 'x')
# Matches the Scala regex in IdentifierRegexes.scala
SIERRA_SYSTEM_NUMBER_RE = re.compile(r"^b[0-9]{7}[0-9x]$")

# Sierra item number, in the same form the Sierra transformer gave item source identifiers
SIERRA_ITEM_NUMBER_RE = re.compile(r"^i[0-9]{7}[0-9x]$")

UUID_RE = re.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def _single_value(values: Iterable[str], source: str) -> str | None:
    distinct = sorted(set(values))
    if len(distinct) > 1:
        raise ValueError(f"Multiple distinct instances of {source}: {distinct}")
    if not distinct:
        return None

    return distinct[0]


def _check_format(
    identifier: str | None, pattern: re.Pattern[str], description: str
) -> str | None:
    if not identifier:
        return None

    if not pattern.match(identifier):
        raise ValueError(
            f"Predecessor identifier does not match {description} format: {identifier!r}"
        )

    return identifier


def extract_predecessor_id(record: Record) -> str | None:
    """Extract predecessor identifier MARC 907 $a."""
    return _single_value(
        (val.lstrip(".") for val in get_a_subfields("907", record)),
        "varfield with tag 907",
    )


def extract_sierra_predecessor_id(record: Record) -> str | None:
    """Extract predecessor Sierra system number from MARC 907 $a."""
    return _check_format(
        extract_predecessor_id(record), SIERRA_SYSTEM_NUMBER_RE, "Sierra system number"
    )


def extract_calm_predecessor_id(record: Record) -> str | None:
    """Extract predecessor CALM record ID from MARC 907 $a."""
    return _check_format(extract_predecessor_id(record), UUID_RE, "CALM record ID")


def extract_sierra_item_predecessor_id(former_ids: Iterable[str]) -> str | None:
    """Extract predecessor Sierra item number from a FOLIO item's formerIds."""
    return _check_format(
        _single_value(former_ids, "formerIds"),
        SIERRA_ITEM_NUMBER_RE,
        "Sierra item number",
    )
