"""
Extract lettering from field
246 - Varying Form of Title, with a second indicator of 6 (caption title)

https://www.loc.gov/marc/bibliographic/bd246.html
"""

from pymarc.record import Record

from adapters.transformers.marc.alternative_titles import is_caption


def extract_lettering(record: Record) -> str | None:
    """Join the ǂa of every caption-title 246 with a blank line, or None if there is none."""
    captions = [
        value.strip()
        for field in record.get_fields("246")
        if is_caption(field)
        for value in field.get_subfields("a")
    ]
    return "\n\n".join(value for value in captions if value) or None
