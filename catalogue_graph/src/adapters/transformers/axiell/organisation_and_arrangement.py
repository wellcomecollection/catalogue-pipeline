"""
Extract organisation and arrangement from field
351 - Organization and Arrangement of Materials
https://www.loc.gov/marc/bibliographic/bd351.html
"""

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.work_data import WorkType

# CALM had additional levels ('subsubsection', 'subsubsubsection', 'subsubseries', ''subsubsubseries),
# which don't exist in Axiell
LEVEL_TO_WORK_TYPE_MAPPING: dict[str, WorkType] = {
    "Collection": "Collection",
    "Section": "Section",
    "sub-section": "Section",
    "series": "Series",
    "sub-series": "Series",
    "Item": "Standard",
    "Item part": "Standard",  # Equivalent of CALM 'piece'
}


def extract_hierarchical_level(record: Record) -> str | None:
    """Extract hierarchical level from 351 $c.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("351", "c", record)
    return values[0] if values else None


def extract_work_type(record: Record) -> WorkType:
    level = extract_hierarchical_level(record)
    if not level:
        raise ValueError(f"Missing hierarchical level (work type) on record {record}.")

    work_type = LEVEL_TO_WORK_TYPE_MAPPING.get(level)
    if work_type is None:
        raise ValueError(f"Unknown hierarchical level '{level}' on record {record}.")

    return work_type


def extract_arrangement(record: Record) -> str | None:
    """Extract arrangement from 351 $b.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("351", "b", record)
    return values[0] if values else None
