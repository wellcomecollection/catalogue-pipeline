"""
Extract designation from field
362 - Dates of Publication and/or Sequential Designation

https://www.loc.gov/marc/bibliographic/bd362.html
"""

from pymarc.record import Record

from transformers.common import get_a_subfields


def extract_designation(record: Record) -> list[str]:
    return get_a_subfields("362", record)
