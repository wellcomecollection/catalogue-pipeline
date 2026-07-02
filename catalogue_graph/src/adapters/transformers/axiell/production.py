"""
Production label is derived from MARC 264. Production dates are derived from MARC 046.
"""

from datetime import UTC, date, datetime, time

from pymarc.record import Record

from adapters.transformers.axiell.dates import (
    extract_production_end_date,
    extract_production_start_date,
)
from adapters.transformers.ebsco.parsers.period import parse_period
from adapters.transformers.marc.common import (
    non_empty_subfields,
)
from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Unidentifiable
from models.pipeline.production import ProductionEvent


def _day_start(d: date) -> datetime:
    return datetime.combine(d, time.min, tzinfo=UTC)


def _day_end(d: date) -> datetime:
    return datetime.combine(d, time.max, tzinfo=UTC)


def _period_from_dates(
    production_label: str, start_date: date, end_date: date
) -> Period:
    formatted_start = _day_start(start_date).strftime("%Y-%m-%dT%H:%M:%SZ")

    # Reproduce the nanosecond precision of the Scala pipeline.
    formatted_end = _day_end(end_date).strftime("%Y-%m-%dT%H:%M:%S.%f") + "999Z"
    date_range = DateTimeRange.model_validate(
        {
            "from": formatted_start,
            "to": formatted_end,
            "label": production_label,
        }
    )

    return Period(label=production_label, range=date_range, id=Unidentifiable())


def extract_production(record: Record) -> list[ProductionEvent]:
    production_labels = non_empty_subfields("264", "c", record)
    start_date = extract_production_start_date(record)
    end_date = extract_production_end_date(record)

    if not production_labels:
        return []

    # Unlike CALM, Axiell usually returns parsed start and end production dates (stored in MARC 046).
    # If available, and if there is exactly one production label, use the provided dates instead
    # of trying to parse the period ourselves from the production label.
    if len(production_labels) == 1 and start_date is not None and end_date is not None:
        periods = [_period_from_dates(production_labels[0], start_date, end_date)]
    else:
        periods = [parse_period(label) for label in production_labels]

    production_label = " ".join(production_labels)
    event = ProductionEvent(
        label=production_label, dates=periods, places=[], agents=[], function=None
    )
    return [event]
