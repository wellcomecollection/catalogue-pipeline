"""
Production events from MARC 260 and 264, with 008 filling in missing dates.
https://www.loc.gov/marc/bibliographic/bd260.html
https://www.loc.gov/marc/bibliographic/bd264.html
https://www.loc.gov/marc/bibliographic/bd008a.html
"""

from collections.abc import Iterable
from typing import Literal

import structlog
from pymarc.field import Field
from pymarc.record import Record

from adapters.transformers.marc.parsers.field008 import Field008
from adapters.transformers.marc.period import parse_period, period_from_span
from adapters.transformers.utils.text_utils import normalise_label
from models.pipeline.concept import Concept, Period
from models.pipeline.production import ProductionEvent
from utils.types import RawConceptType

logger = structlog.get_logger(__name__)

IND2_264_MAP = {
    "0": "Production",
    "1": "Publication",
    "2": "Distribution",
    "3": "Manufacture",
}


def extract_production(
    record: Record, prefer: Literal["260", "264"] = "260"
) -> list[ProductionEvent]:
    """Production events from 260 or 264, using 008 when they give no parseable date.

    A record with both 260 and 264 yields the events of the preferred tag only.
    """
    fallback = "264" if prefer == "260" else "260"
    events = _EVENTS_BY_TAG[prefer](record) or _EVENTS_BY_TAG[fallback](record)
    event_008 = _event_from_008(record)

    if event_008 is None:
        return events
    if not events:
        return [event_008]
    # A date that degraded to label-only (no range) isn't useful for
    # filtering/sorting. Take the 008 range but keep the catalogued label.
    if all(date.range is None for date in events[0].dates):
        events[0] = _with_date_from(events[0], event_008.dates[0])
    return events


def _with_date_from(event: ProductionEvent, donor: Period) -> ProductionEvent:
    label = (
        event.dates[0].label if event.dates and event.dates[0].label else donor.label
    )
    return event.model_copy(
        update={"dates": [donor.model_copy(update={"label": label})]}
    )


def _events_from_260(record: Record) -> list[ProductionEvent]:
    return _non_empty(_event_from_260(field) for field in record.get_fields("260"))


def _events_from_264(record: Record) -> list[ProductionEvent]:
    return _non_empty(_event_from_264(field) for field in record.get_fields("264"))


_EVENTS_BY_TAG = {"260": _events_from_260, "264": _events_from_264}


def _non_empty(events: Iterable[ProductionEvent | None]) -> list[ProductionEvent]:
    return [event for event in events if event is not None and event.label]


def _event_from_260(field: Field) -> ProductionEvent:
    places = _concepts(field, "a", "Place")
    agents = _concepts(field, "b", "Agent")
    dates = _dates(field, "c")
    function = None
    # ǂe, ǂf and ǂg describe manufacture. They always follow the ǂa, ǂb and ǂc
    # values, whatever their order in the MARC.
    if field.get_subfields("e", "f", "g"):
        places += _concepts(field, "e", "Place")
        agents += _concepts(field, "f", "Agent")
        dates += _dates(field, "g")
        function = Concept(label="Manufacture")
    return ProductionEvent(
        label=_label(field),
        places=places,
        agents=agents,
        dates=dates,
        function=function,
    )


def _event_from_264(field: Field) -> ProductionEvent | None:
    # Copyright notices (4) go elsewhere in the model; a blank indicator says nothing.
    if field.indicator2 in ("4", " "):
        return None
    if (function := IND2_264_MAP.get(field.indicator2)) is None:
        logger.error(
            "Unrecognised second indicator for production function",
            indicator2=field.indicator2,
        )
        return None
    return ProductionEvent(
        label=_label(field),
        places=_concepts(field, "a", "Place"),
        agents=_concepts(field, "b", "Agent"),
        dates=_dates(field, "c"),
        function=Concept(label=function),
    )


def _event_from_008(record: Record) -> ProductionEvent | None:
    field008 = Field008.from_record(record)
    if field008 is None or (dates := field008.dates) is None:
        return None
    place = field008.place_of_production
    return ProductionEvent(
        label=dates.label,
        places=[_concept(place, "Place")] if place else [],
        agents=[],
        dates=[period_from_span(dates.label, dates.span)],
        function=None,
    )


def _label(field: Field) -> str:
    # Free text, every subfield in order with no trimming.
    return " ".join(subfield.value for subfield in field.subfields)


def _concept(label: str, concept_type: RawConceptType) -> Concept:
    return Concept(
        label=normalise_label(label, concept_type, preserve_trailing_period=True),
        type=concept_type,
    )


def _concepts(field: Field, code: str, concept_type: RawConceptType) -> list[Concept]:
    return [_concept(value, concept_type) for value in field.get_subfields(code)]


def _dates(field: Field, code: str) -> list[Period]:
    return [
        parse_period(normalise_label(value, "Period"))
        for value in field.get_subfields(code)
    ]
