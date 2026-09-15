"""
Extract designation from field
362 - Dates of Publication and/or Sequential Designation

https://www.loc.gov/marc/bibliographic/bd362.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty, non_repeatable_subfield


def extract_designation(record: Record) -> list[str]:
    return non_empty(
        non_repeatable_subfield(field, "a") for field in record.get_fields("362")
    )
