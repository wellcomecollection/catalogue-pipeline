from datetime import date

import structlog
from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield

logger = structlog.get_logger(__name__)


def _try_parse_date(s: str) -> date | None:
    """Try to parse date in yyyy-M-d format (e.g. 2039-01-01). Return `None` if not valid date."""
    try:
        y, m, d = s.split("-")
        parsed = date(int(y), int(m), int(d))
        return parsed
    except (ValueError, TypeError):
        logger.warning("Could not parse date", value=s)

    return None


def extract_closed_until_date(record: Record) -> date | None:
    value = first_non_empty_subfield("506", "g", record)
    return _try_parse_date(value) if value else None


def extract_restricted_until_date(record: Record) -> date | None:
    value = first_non_empty_subfield("540", "g", record)
    return _try_parse_date(value) if value else None
