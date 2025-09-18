"""
Extracting holdings information from
856 - Electronic Location and Access (R)
https://www.loc.gov/marc/bibliographic/bd856.html
"""

import contextlib
from collections.abc import Iterator

from pymarc.record import Record

from models.work import (
    AccessCondition,
    DigitalLocation,
    Holdings,
    LicensedResource,
    OnlineResource,
    ViewOnline,
)
from transformers.common import is_url


def extract_holdings(record: Record) -> list[Holdings]:
    return [
        Holdings(
            enumeration=[enumeration],
            location=DigitalLocation(
                url=url,
                link_text=link_text,
                location_type=OnlineResource,
                access_conditions=[
                    AccessCondition(method=ViewOnline, status=LicensedResource)
                ],
            ),
        )
        for (enumeration, link_text, url) in extract_holdings_values(record)
    ]


def extract_holdings_values(record: Record) -> Iterator[tuple[str, str, str]]:
    for field in record.get_fields("856"):
        with contextlib.suppress(KeyError):
            enumeration = field["3"]  # 3 is non-repeating
            # u and z are repeatable, but we only take the first one.
            # In the existing EBSCO data, they are not repeated
            url = field["u"]
            if not is_url(url):
                continue
            # `z` is actually "Public note", link text is `y`
            # but z is what is currently used, and no values of `y` are
            # present in EBSCO data
            link_text = field["z"]
            yield enumeration, link_text, url
