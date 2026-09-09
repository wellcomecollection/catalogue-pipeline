"""
Extracting former frequency from the MARC Former Publication Frequency field
https://www.loc.gov/marc/bibliographic/bd321.html
"""

from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty


def extract_former_frequency(record: Record) -> list[str]:
    """321 is repeatable: each occurrence becomes its own entry."""
    return non_empty(format_field(field) for field in record.get_fields("321"))


def format_field(field: Field) -> str:
    return " ".join(value.strip() for value in field.get_subfields("a", "b")).strip()
