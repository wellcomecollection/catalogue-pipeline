from datetime import date, datetime

import structlog
from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield

logger = structlog.get_logger(__name__)


def _parse_iso_date(s: str) -> date | None:
    """Parse a date in yyyy-M-d format (e.g. 2039-01-01). Return `None` if not a valid date."""
    try:
        return datetime.strptime(s, "%Y-%m-%d").date()
    except (ValueError, TypeError):
        return None


def _try_parse_date(s: str) -> date | None:
    parsed = _parse_iso_date(s)
    if parsed is None:
        logger.warning("Could not parse date", value=s)
    return parsed


def _date_from(record: Record, field: str, subfield: str) -> date | None:
    value = first_non_empty_subfield(field, subfield, record)
    return _try_parse_date(value) if value else None


def extract_closed_until_date(record: Record) -> date | None:
    return _date_from(record, "506", "g")


def extract_restricted_until_date(record: Record) -> date | None:
    return _date_from(record, "540", "g")


def _parse_production_date(value: str, month: int, day: int) -> date | None:
    """Axiell stores parsed production dates at the precision it knows: a full
    yyyy-mm-dd date for exact dates, or a bare year (e.g. "1901") for approximate
    ones. Bare years are widened to the given month and day."""
    parsed = _parse_iso_date(value)
    if parsed is not None:
        return parsed

    if value.isdigit() and len(value) <= 4 and int(value) > 0:
        return date(int(value), month, day)

    logger.warning("Could not parse production date", value=value)
    return None


def _production_date_from(
    record: Record, subfield: str, month: int, day: int
) -> date | None:
    value = first_non_empty_subfield("046", subfield, record)
    return _parse_production_date(value, month, day) if value else None


def extract_production_start_date(record: Record) -> date | None:
    return _production_date_from(record, "k", month=1, day=1)


def extract_production_end_date(record: Record) -> date | None:
    return _production_date_from(record, "l", month=12, day=31)
