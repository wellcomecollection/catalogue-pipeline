"""
Extract designation from field
362 - Dates of Publication and/or Sequential Designation

https://www.loc.gov/marc/bibliographic/bd362.html
"""

from collections.abc import Iterable

from pymarc.record import Record


def extract_designation(record: Record) -> list[str]:
    return non_empty(field.get("a", "").strip() for field in record.get_fields("362"))


def non_empty(designations: Iterable[str | None]) -> list[str]:
    return [designation for designation in designations if designation]
