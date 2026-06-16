"""
Extract catalogue status from private field
583 - Action Note
    $l - Status
https://www.loc.gov/marc/bibliographic/bd583.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty


def extract_catalogue_status(record: Record) -> str | None:
    """Extract catalogue status from 583 $l (ind1=0, private).

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty(
        field.get("l", "").strip()
        for field in record.get_fields("583")
        if field.indicator1 == "0"
    )
    return values[0] if values else None
