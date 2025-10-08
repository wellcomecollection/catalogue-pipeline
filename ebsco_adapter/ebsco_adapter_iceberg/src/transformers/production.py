"""
Production is derived from field 260 or 264, and also 008
In EBSCO data, prefer field 260 over 264 but use 264 if 260 is absent or invalid
In addition, use the date segments from 008 to add another production event.
"""

from collections.abc import Iterable

from pymarc.field import Field
from pymarc.record import Record
from parsers.field008 import Field008
from parsers.period import parse_period
from models.work import DateTimeRange, Period, ProductionEvent, SourceConcept


def extract_production(record: Record) -> list[ProductionEvent]:
    production008 = extract_production_from_008
    if productions := extract_production_from_fields(record.get_fields("260")):
        return productions
    return extract_production_from_fields(record.get_fields("264"))


def extract_production_from_008(record: Record) -> ProductionEvent | None:
    field008 = Field008(record.get_fields("008"))
    date_range_str = field008.maximal_date_range
    place_str = field008.place_of_production
    period = parse_period(date_range_str)
    if period:
        return ProductionEvent(
            label=date_range_str,
            agents=[],
            dates=[period],
            places=[SourceConcept(label=place_str, type="Place")],
            function=None
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
