"""
Extract designation from field
362 - Dates of Publication and/or Sequential Designation

https://www.loc.gov/marc/bibliographic/bd362.html
"""

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty

logger = structlog.get_logger(__name__)


def extract_designation(record: Record) -> list[str]:
    return non_empty(_designation(field) for field in record.get_fields("362"))


def _designation(field: Field) -> str:
    """362 ǂa is a non-repeatable field. If it does repeat, log an error and take the first value."""
    values = field.get_subfields("a")
    if len(values) > 1:
        logger.error(
            "Repeated non-repeating subfield $a",
            tag=field.tag,
            count=len(values),
        )
    return values[0].strip() if values else ""
