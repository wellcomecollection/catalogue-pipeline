"""
Extracting current frequency from the MARC Current Publication Frequency field
https://www.loc.gov/marc/bibliographic/bd310.html
"""

from pymarc.field import Field
from pymarc.record import Record


def extract_current_frequency(record: Record) -> str | None:
    return (
        " ".join(format_field(field) for field in record.get_fields("310")).strip()
        or None
    )


def format_field(field: Field) -> str:
    return " ".join(value.strip() for value in field.get_subfields("a", "b"))
