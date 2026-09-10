"""
Extracting descriptions from
https://www.loc.gov/marc/bibliographic/bd520.html

Descriptions are compiled from the non-repeating subfields:

$a - Summary, etc. (NR)
$b - Expansion of summary note (NR)
$c - Assigning source (NR)

And any present repeating $u subfields
$u - Uniform Resource Identifier (R)

Each 520 field becomes one <p> paragraph, and the paragraphs are joined with
newlines. Subfields a, b and c are emitted in the order they appear in the
field, followed by any $u subfields.
"""

from collections.abc import Iterator

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty
from adapters.transformers.utils.html import format_as_html_link

logger = structlog.get_logger(__name__)


def extract_description(record: Record) -> str | None:
    paragraphs = non_empty(format_field(field) for field in record.get_fields("520"))
    return "\n".join(paragraphs) or None


def format_field(field: Field) -> str:
    contents = " ".join(get_field_values(field))
    if not contents:
        return ""
    return f"<p>{contents}</p>"


def get_field_values(field: Field) -> Iterator[str]:
    """Yield $a, $b and $c in the order they appear, then any $u as links."""
    seen: set[str] = set()
    for code, value in field:
        if code not in ("a", "b", "c"):
            continue
        if code in seen:
            logger.error(
                "Multiple instances of non-repeatable subfield in field 520",
                subfield=code,
            )
            continue
        seen.add(code)
        yield value.strip()

    for value in field.get_subfields("u"):
        yield format_as_html_link(value)
