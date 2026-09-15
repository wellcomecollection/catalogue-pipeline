"""
Extract duration in seconds from the MARC Playing Time field
https://www.loc.gov/marc/bibliographic/bd306.html
"""

import re

from pymarc.record import Record

# Six numeric characters in the pattern hhmmss, as required by the field definition.
HHMMSS = re.compile(r"[0-9]{6}")


def extract_duration(record: Record) -> int | None:
    """Total playing time in seconds, from the first 306 ǂa read as hhmmss"""
    values = [
        value
        for field in record.get_fields("306")
        for value in field.get_subfields("a")
    ]
    if not values:
        return None
    return _parse_hhmmss(values[0])


def _parse_hhmmss(value: str) -> int | None:
    # Ignore all values which are not exactly six digits.
    if not HHMMSS.fullmatch(value):
        return None

    hours, minutes, seconds = (int(value[i : i + 2]) for i in range(0, 6, 2))
    return hours * 3600 + minutes * 60 + seconds
