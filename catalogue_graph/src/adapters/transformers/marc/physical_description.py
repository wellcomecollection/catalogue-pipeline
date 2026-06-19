"""
Extract physical description from field
300 - Physical Description
https://www.loc.gov/marc/bibliographic/bd300.html

Subfields used:
  $a - extent
  $b - other physical details
  $c - dimensions
  $e - accompanying material

Multiple physical descriptions are joined with <br/>.
"""

from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty


def _field_content(field: Field) -> str:
    return " ".join(field.get_subfields("a", "b", "c", "e")).strip()


def extract_physical_description(record: Record) -> str | None:
    lines = non_empty(_field_content(field) for field in record.get_fields("300"))

    if not lines:
        return None
    return "<br/>".join(lines)
