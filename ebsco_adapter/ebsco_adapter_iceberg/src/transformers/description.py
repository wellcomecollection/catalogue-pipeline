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
from urllib.parse import urlparse

from pymarc.field import Field
from pymarc.record import Record

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
    return f'<a href="{maybe_url}">{maybe_url}</a>' if is_url(maybe_url) else maybe_url


def is_url(maybe_url: str) -> bool:
    """
    A potential URL is only considered a URL for linking purposes if it
    has a webpage-appropriate scheme and would actually go somewhere.

    This test is actually slightly stricter than the old Scala way, which
    would allow jar:// and ftp:// urls, but in the data seen so far
    from EBSCO, all $u subfields are http urls.
    """
    url = urlparse(maybe_url)
    # urlparse is very lenient in what it accepts and parses.
    # e.g. If it's not a fully qualified URL, it is interpreted as relative.
    #
    # So we need to do a bit of extra checking to see if it really
    # looks like a URL.
    is_linkable = bool(url.scheme in ["http", "https"] and url.netloc)
    if not is_linkable:
        logger.warning("has MARC 520 $u which doesn't look like a URL: %s", maybe_url)
    return is_linkable
