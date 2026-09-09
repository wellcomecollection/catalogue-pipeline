"""
Extracting current frequency from the MARC Current Publication Frequency field
https://www.loc.gov/marc/bibliographic/bd310.html
"""

from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty


def extract_current_frequency(record: Record) -> str | None:
    """Join all 310s with a space, ignoring fields with no ǂa or ǂb content."""
    fields = non_empty(format_field(field) for field in record.get_fields("310"))
    return " ".join(fields) or None


def format_field(field: Field) -> str:
    """Join ǂa and ǂb with a space, ignoring subfields with no content."""
    values = non_empty(value.strip() for value in field.get_subfields("a", "b"))
    return " ".join(values)
