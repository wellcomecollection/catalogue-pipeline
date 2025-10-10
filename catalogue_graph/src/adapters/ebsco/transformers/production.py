"""
Production is derived from field 260 or 264, and also 008
In EBSCO data, prefer field 260 over 264 but use 264 if 260 is absent or invalid
In addition, use the date segments from 008 to add another production event.
"""

from collections.abc import Iterable

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.models.work import (
    ProductionEvent,
    SourceConcept,
)
from adapters.ebsco.transformers.parsers.field008 import Field008
from adapters.ebsco.transformers.parsers.period import parse_period


def extract_production(record: Record) -> list[ProductionEvent]:
    production008 = extract_production_from_008(record)
    productions260_264 = extract_production_from_fields(
        record.get_fields("260")
    ) or extract_production_from_fields(record.get_fields("264"))
    match (productions260_264, production008):
        case ([], None):
            return []
        case ([], production) if production is not None:
            return [production]
        case (productions, None):
            return productions
        case (productions, production) if production is not None:
            if not productions[0].dates:
                productions[0].dates = production.dates
            return productions
        case _:
            return []


def extract_production_from_008(record: Record) -> ProductionEvent | None:
    field = record.get("008")
    if not (field and field.data):
        return None
    field008 = Field008(field.data)
    date_range_str = field008.maximal_date_range
    if date_range_str is not None:
        period = parse_period(date_range_str)
        if period:
            place = field008.place_of_production
            places = [SourceConcept(label=place, type="Place")] if place else []
            return ProductionEvent(
                label=date_range_str,
                places=places,
                agents=[],
                dates=[period],
                function=None,
            )
    return None


def extract_production_from_fields(fields: Iterable[Field]) -> list[ProductionEvent]:
    return [
        production
        for production in (single_production_event(field) for field in fields)
        if production is not None and production.label
    ]


IND2_264_MAP = {
    "0": "Production",
    "1": "Publication",
    "2": "Distribution",
    "3": "Manufacture",
}


def single_production_event(field: Field) -> ProductionEvent | None:
    if field.tag not in ["260", "264"]:
        raise ValueError(
            f"Unexpected production event field: {field.tag}. Should be one of 260, 264"
        )
    label = field.format_field()
    places = [
        SourceConcept(label=subfield, type="Place")
        for subfield in field.get_subfields("a")
    ]
    agents = [
        SourceConcept(label=subfield, type="Agent")
        for subfield in field.get_subfields("b")
    ]
    dates = [parse_period(subfield) for subfield in field.get_subfields("c")]
    function = None
    if field.tag == "260" and field.get_subfields("e", "f", "g"):
        places += [
            SourceConcept(label=subfield, type="Place")
            for subfield in field.get_subfields("e")
        ]
        agents += [
            SourceConcept(label=subfield, type="Agent")
            for subfield in field.get_subfields("f")
        ]
        dates += [parse_period(subfield) for subfield in field.get_subfields("g")]
        function = SourceConcept(label="Manufacture")

    if field.tag == "264":
        if field.indicator2 in ["4", " "]:
            return None
        else:
            function = SourceConcept(label=IND2_264_MAP[field.indicator2])

    return ProductionEvent(
        label=label, places=places, agents=agents, dates=dates, function=function
    )
