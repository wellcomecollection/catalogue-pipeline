"""A Period concept from a free-text date, using the period parser for its range."""

from adapters.transformers.marc.parsers.period import Source, Span, parse
from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Identifiable, Unidentifiable


def parse_period(
    label: str,
    identifier: Identifiable | Unidentifiable | None = None,
    source: Source = "marc",
) -> Period:
    """A Period for the label, with a range when the label can be read as dates."""
    return period_from_span(label, parse(label, source), identifier)


def period_from_span(
    label: str,
    span: Span | None,
    identifier: Identifiable | Unidentifiable | None = None,
) -> Period:
    """A Period for the label covering the span's days, or without a range when there is no span."""
    date_range = None
    if span:
        from_ = span[0].isoformat() + "T00:00:00Z"
        # the Scala pipeline's end of day has nanosecond precision
        to_ = span[1].isoformat() + "T23:59:59.999999999Z"
        date_range = DateTimeRange(label=label, **{"from": from_, "to": to_})
    return Period(label=label, range=date_range, id=identifier or Unidentifiable())
