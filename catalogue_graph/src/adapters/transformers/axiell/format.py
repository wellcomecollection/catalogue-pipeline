"""
Extract format values from field
655 - Index Term - Genre/Form
    $a - Genre/form data
    $2 - Source of term
https://www.loc.gov/marc/bibliographic/bd655.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty
from models.pipeline.format import ArchivesAndManuscripts, ArchivesDigital, Format


def extract_raw_formats(record: Record) -> list[str]:
    """Extract format values from 655 $a where ind2=7 and $2=local.

    Returns all non-empty matching values.
    """
    return non_empty(
        field.get("a", "").strip()
        for field in record.get_fields("655")
        if field.indicator2 == "7" and field.get("2", "").strip() == "local"
    )


def extract_format(record: Record) -> Format:
    # Unlike CALM records, Axiell records can have more than one format
    raw_formats = extract_raw_formats(record)

    # TODO: This is a port of the Scala logic, but do we want to change it?
    # There are some other format values which might fall under ArchivesDigital, such as 'digital'
    if "Archives - Digital" in raw_formats:
        return ArchivesDigital

    return ArchivesAndManuscripts
