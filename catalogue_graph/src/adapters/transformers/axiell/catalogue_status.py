"""
Extract catalogue status from private field
583 - Action Note
    $l - Status
https://www.loc.gov/marc/bibliographic/bd583.html
"""

from typing import Literal, get_args

from pymarc.record import Record

from adapters.transformers.marc.common import non_empty

AxiellCatalogueStatus = Literal[
    "Catalogued", "draft", "partially complete", "In progress"
]


def extract_catalogue_status(record: Record) -> AxiellCatalogueStatus | None:
    """Extract catalogue status from 583 $l (ind1=0, private).

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty(
        field.get("l", "").strip()
        for field in record.get_fields("583")
        if field.indicator1 == "0"
    )

    if not values:
        return None

    value = values[0]
    if value not in get_args(AxiellCatalogueStatus):
        raise ValueError(
            f"Unexpected Axiell catalogue status (record progress) value '{value}' for record {record}."
        )

    return values[0] if values else None
