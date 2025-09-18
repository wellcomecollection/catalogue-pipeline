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

from transformers.common import is_url

logger = logging.getLogger("transformer/description")


def extract_description(record: Record) -> str | None:
    return (
        "\n".join(format_field(field) for field in record.get_fields("520")).strip()
        or None
    )


def format_field(field: Field) -> str:
    return " ".join(chain(get_plain_field_values(field), get_u_field_values(field)))


def get_plain_field_values(field: Field) -> Iterable[str]:
    return (value.strip() for value in field.get_subfields("a", "b", "c"))


def get_u_field_values(field: Field) -> Iterable[str]:
    return (format_as_link(value) for value in field.get_subfields("u"))


def format_as_link(maybe_url: str) -> str:
    if is_url(maybe_url):
        return f'<a href="{maybe_url}">{maybe_url}</a>'
    else:
        logger.warning("has MARC 520 $u which doesn't look like a URL: %s", maybe_url)
        return maybe_url
