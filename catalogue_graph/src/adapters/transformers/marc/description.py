"""
Extracting descriptions from
https://www.loc.gov/marc/bibliographic/bd520.html

Descriptions are compiled from the non-repeating subfields:

$a - Summary, etc. (NR)
$b - Expansion of summary note (NR)
$c - Assigning source (NR)

And any present repeating $u subfields
$u - Uniform Resource Identifier (R)
"""

import logging
from collections.abc import Iterable
from itertools import chain

from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.utils.html import format_as_html_link

logger = logging.getLogger("transformer/description")


def extract_description(record: Record) -> str | None:
    return (
        "\n".join(format_field(field) for field in record.get_fields("520")).strip()
        or None
    )


def format_field(field: Field) -> str:
    contents = " ".join(chain(get_plain_field_values(field), get_u_field_values(field)))
    return f"<p>{contents}</p>"


def get_plain_field_values(field: Field) -> Iterable[str]:
    return (value.strip() for value in field.get_subfields("a", "b", "c"))


def get_u_field_values(field: Field) -> Iterable[str]:
    return (format_as_html_link(value) for value in field.get_subfields("u"))
