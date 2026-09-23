"""A Period concept from a free-text date, using the period parser for its range."""

from adapters.transformers.marc.parsers.period import Source, parse
from models.pipeline.concept import DateTimeRange, Period
from models.pipeline.identifier import Identifiable, Unidentifiable


def parse_period(
    label: str,
    identifier: Identifiable | Unidentifiable | None = None,
    source: Source = "marc",
) -> Period:
    """A Period for the label, with a range when the label can be read as dates."""
    span = parse(label, source)
    date_range = (
        DateTimeRange(
            label=label,
            **{
                "from": span[0].isoformat() + "T00:00:00Z",
                # the Scala pipeline's end of day has nanosecond precision
                "to": span[1].isoformat() + "T23:59:59.999999999Z",
            },
        )
        if span
        else None
    )
    return Period(label=label, range=date_range, id=identifier or Unidentifiable())
