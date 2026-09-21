"""
Extract duration in seconds from the MARC Playing Time field
https://www.loc.gov/marc/bibliographic/bd306.html
"""

import re

from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield

# Six numeric characters in the pattern hhmmss, as required by the field definition.
HHMMSS = re.compile(r"[0-9]{6}")


def extract_duration(record: Record) -> int | None:
    """Total playing time in seconds, from the first non-blank 306 ǂa read as hhmmss"""
    value = first_non_empty_subfield("306", "a", record)
    if value is None:
        return None
    return _parse_hhmmss(value)


def _parse_hhmmss(value: str) -> int | None:
    # Ignore all values which are not exactly six digits.
    if not HHMMSS.fullmatch(value):
        return None

    # Strictly speaking, durations with `minutes` > 60 or `seconds` > 60 are invalid. We parse them anyway, mirroring
    # the Scala transformer. There are a few Sierra works with `minutes` > 60, and parsing them gives the duration the
    # cataloguer intended. For example, `b28511414` has an invalid duration of `008000`, which parses as 80 minutes,
    # and its 300 field (`1 videocassette (80 min.)`) corroborates that value.
    # TODO: Durations are mostly unpopulated in Folio. Revisit this after the next migration.
    hours, minutes, seconds = (int(value[i : i + 2]) for i in range(0, 6, 2))
    return hours * 3600 + minutes * 60 + seconds
