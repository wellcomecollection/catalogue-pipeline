from collections.abc import Iterable

from pymarc.field import Field
from pymarc.record import Record

from models.work import DateTimeRange, Period, ProductionEvent, SourceConcept


def extract_production(record: Record) -> list[ProductionEvent]:
    if productions := extract_production_from_fields(record.get_fields("260")):
        return productions
    return extract_production_from_fields(record.get_fields("264"))


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


def parse_period(period: str) -> Period:
    # TODO: parse the period string into a range
    r = DateTimeRange.model_validate({"label": period, "from": "", "to": ""})
    return Period(label=period, range=r)
