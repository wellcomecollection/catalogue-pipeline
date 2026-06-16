"""
Extract access status from field
351 - Restrictions on Access Note
    $f - Standardized terminology for access restriction
https://www.loc.gov/marc/bibliographic/bd351.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields


def extract_access_status(record: Record) -> str | None:
    """Extract access status from 506 $f.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("506", "f", record)
    return values[0] if values else None
