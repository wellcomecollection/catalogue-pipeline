"""
Production is derived from field 260 or 264, and also 008
In EBSCO data, prefer field 260 over 264 but use 264 if 260 is absent or invalid
In addition, use the date segments from 008 to add another production event.
"""

from datetime import UTC, date, datetime, time

from pymarc.record import Record

from adapters.transformers.axiell.dates import (
    extract_production_end_date,
    extract_production_start_date,
)
from adapters.transformers.marc.common import first_non_empty_subfield
from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Unidentifiable
from models.pipeline.production import ProductionEvent


def _day_start(d: date) -> date:
    return datetime.combine(d, time.min, tzinfo=UTC)


def _day_end(d: date) -> date:
    return datetime.combine(d, time.max, tzinfo=UTC)


def _get_range_label(start_date: date, end_date: date) -> str:
    if start_date == end_date:
        return f"{start_date.day} {start_date.strftime('%B %Y')}"

    if start_date.year == end_date.year:
        if start_date.month == end_date.month:
            return f"{start_date.strftime('%B %Y')}"

        return f"{start_date.year}"

    return f"{start_date.year}-{end_date.year}"


def extract_production(record: Record) -> list[ProductionEvent]:
    production_label = first_non_empty_subfield("264", "c", record)
    start_date = extract_production_start_date(record)
    end_date = extract_production_end_date(record)

    if production_label is None or start_date is None or end_date is None:
        return []

    time_range_label = _get_range_label(start_date, end_date)

    period = Period(
        label=production_label,
        range=DateTimeRange(
            **{
                "from": _day_start(start_date).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "to": _day_end(end_date).strftime("%Y-%m-%dT%H:%M:%S.%f") + "999Z",
                "label": time_range_label,
            }
        ),
        id=Unidentifiable(),
    )

    event = ProductionEvent(
        label=production_label, dates=[period], places=[], agents=[], function=None
    )
    return [event]
