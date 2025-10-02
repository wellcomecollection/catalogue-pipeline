"""
Extract edition from field
250 - Edition Statement

https://www.loc.gov/marc/bibliographic/bd250.html
"""

from pymarc.record import Record

from transformers.common import get_a_subfields


def extract_edition(record: Record) -> str | None:
    return " ".join(get_a_subfields("250", record)) or None
