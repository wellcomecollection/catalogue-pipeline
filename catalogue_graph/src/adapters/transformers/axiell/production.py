"""
Production label is derived from MARC 264. Production dates are derived from MARC 046.
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


def extract_production(record: Record) -> list[ProductionEvent]:
    production_label = first_non_empty_subfield("264", "c", record)
    start_date = extract_production_start_date(record)
    end_date = extract_production_end_date(record)

    if production_label is None:
        return []

    date_range = None
    if start_date is not None and end_date is not None:
        date_range = DateTimeRange.model_validate(
            {
                "from": _day_start(start_date).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "to": _day_end(end_date).strftime("%Y-%m-%dT%H:%M:%S.%f") + "999Z",
                "label": production_label,
            }
        )

    period = Period(label=production_label, range=date_range, id=Unidentifiable())
    event = ProductionEvent(
        label=production_label, dates=[period], places=[], agents=[], function=None
    )
    return [event]
