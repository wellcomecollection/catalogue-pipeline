from datetime import date, datetime

import structlog
from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield

logger = structlog.get_logger(__name__)


def _try_parse_date(s: str) -> date | None:
    """Try to parse date in yyyy-M-d format (e.g. 2039-01-01). Return `None` if not valid date."""
    try:
        return datetime.strptime(s, "%Y-%m-%d").date()
    except (ValueError, TypeError):
        logger.warning("Could not parse date", value=s)

    return None


def _date_from(record: Record, field: str, subfield: str) -> date | None:
    value = first_non_empty_subfield(field, subfield, record)
    return _try_parse_date(value) if value else None


def extract_closed_until_date(record: Record) -> date | None:
    return _date_from(record, "506", "g")


def extract_restricted_until_date(record: Record) -> date | None:
    return _date_from(record, "540", "g")


def extract_production_start_date(record: Record) -> date | None:
    return _date_from(record, "046", "k")


def extract_production_end_date(record: Record) -> date | None:
    return _date_from(record, "046", "l")
