"""
Extract organisation and arrangement from field
351 - Organization and Arrangement of Materials
https://www.loc.gov/marc/bibliographic/bd351.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields


def extract_hierarchical_level(record: Record) -> str | None:
    """Extract hierarchical level from 351 $c.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("351", "c", record)
    return values[0] if values else None


def extract_arrangement(record: Record) -> str | None:
    """Extract arrangement from 351 $b.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("351", "b", record)
    return values[0] if values else None
