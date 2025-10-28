import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record

from ..helpers import lone_element


def test_no_production(marc_record: Record) -> None:
    assert transform_record(marc_record).data.production == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    subfields=[],
                )
            ],
            id=f"MARC field code: {code}",
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_empty_production_is_no_production(marc_record: Record) -> None:
    assert transform_record(marc_record).data.production == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="008",
                    data="800121d19791995acafr p o o   0    0engrc",
                ),
                Field(
                    tag="260",
                    subfields=[],
                ),
                Field(
                    tag="264",
                    subfields=[],
                ),
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_fall_back_to_008(marc_record: Record) -> None:
    production = lone_element(transform_record(marc_record).data.production)
    assert lone_element(production.places).label == "Australian Capital Territory"
    period = lone_element(production.dates)
    assert period.range.label == "1979-1995"
    assert period.range.from_time == "1979-01-01T00:00:00Z"
    assert period.range.to_time == "1995-12-31T23:59:59.999999Z"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    indicators=Indicators(" ", "1"),
                    subfields=[
                        Subfield(code="c", value="1998"),
                        Subfield(code="b", value="Mankind"),
                        Subfield(code="a", value="Announcer's Table"),
                    ],
                )
            ],
            id=f"MARC field code: {code}",
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_production_from_abc(marc_record: Record) -> None:
    production = lone_element(transform_record(marc_record).data.production)
    assert production.label == "1998 Mankind Announcer's Table"
    assert lone_element(production.places).label == "Announcer's Table"
    assert lone_element(production.agents).label == "Mankind"
    period = lone_element(production.dates)
    assert period.range.label == "1998"
    assert period.range.from_time == "1998-01-01T00:00:00Z"
    assert period.range.to_time == "1998-12-31T23:59:59.999999Z"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="008",
                    data="800121d18001899acafr p o o   0    0engrc",
                ),
                Field(
                    tag=code,
                    indicators=Indicators(" ", "1"),
                    subfields=[
                        Subfield(code="c", value="1998"),
                        Subfield(code="b", value="Mankind"),
                        Subfield(code="a", value="Announcer's Table"),
                    ],
                ),
            ],
            id=f"MARC field code: {code}",
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_ignores_008(marc_record: Record) -> None:
    """
    008 is to be ignored if the information in 260/264 is complete
    """
    production = lone_element(transform_record(marc_record).data.production)
    assert production.label == "1998 Mankind Announcer's Table"
    assert lone_element(production.places).label == "Announcer's Table"
    assert lone_element(production.agents).label == "Mankind"
    period = lone_element(production.dates)
    assert period.range.label == "1998"
    assert period.range.from_time == "1998-01-01T00:00:00Z"
    assert period.range.to_time == "1998-12-31T23:59:59.999999Z"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    indicators=Indicators(" ", "1"),
                    subfields=[
                        Subfield(code="c", value="1998"),
                        Subfield(code="b", value="Mankind"),
                        Subfield(code="a", value="Announcer's Table"),
                        Subfield(code="c", value="nineteen ninety eight"),
                        Subfield(code="b", value="Undertaker"),
                        Subfield(code="a", value="Hell in a Cell"),
                    ],
                )
            ],
            id=f"MARC field code: {code}",
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_production_multiple_subfields(marc_record: Record) -> None:
    production = lone_element(transform_record(marc_record).data.production)
    assert (
        production.label
        == "1998 Mankind Announcer's Table nineteen ninety eight Undertaker Hell in a Cell"
    )
    assert production.places[0].label == "Announcer's Table"
    assert production.places[1].label == "Hell in a Cell"
    assert production.agents[0].label == "Mankind"
    assert production.agents[1].label == "Undertaker"
    assert production.dates[0].label == "1998"
    assert production.dates[1].label == "nineteen ninety eight"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag=code,
                    indicators=Indicators(" ", "2"),
                    subfields=[Subfield(code="a", value="London")],
                ),
                Field(
                    tag=code,
                    indicators=Indicators(" ", "1"),
                    subfields=[Subfield(code="a", value="Paris")],
                ),
            ],
            id=f"MARC field code: {code}",
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_multiple_productions(marc_record: Record) -> None:
    productions = transform_record(marc_record).data.production
    assert productions[0].label == "London"
    assert productions[1].label == "Paris"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="260",
                    subfields=[
                        Subfield(code="a", value="New York"),
                        Subfield(code="e", value="Munich"),
                        Subfield(code="f", value="R. Scott"),
                        Subfield(code="g", value="1979"),
                    ],
                )
            ],
        )
    ],
    indirect=["marc_record"],
)
def test_manufacture_fields(marc_record: Record) -> None:
    """
    A 260 field with any of the manufacture subfields e,f,g
    has a production function of "Manufacture",
    Each of the manufacture subfields will be added to the appropriate
    places/agents/dates field
    """
    production = lone_element(transform_record(marc_record).data.production)
    assert production.function.label == "Manufacture"
    assert production.label == "New York Munich R. Scott 1979"
    assert production.places[1].label == "Munich"
    assert lone_element(production.agents).label == "R. Scott"
    assert lone_element(production.dates).label == "1979"


@pytest.mark.parametrize(
    "marc_record, production_function",
    [
        pytest.param(
            [
                Field(
                    tag="264",
                    indicators=Indicators(" ", ind2),
                    subfields=[
                        Subfield(code="a", value="New York"),
                    ],
                )
            ],
            fn,
            id=f"260: {ind2}->{fn}",
        )
        for (ind2, fn) in [
            ("0", "Production"),
            ("1", "Publication"),
            ("2", "Distribution"),
            ("3", "Manufacture"),
        ]
    ],
    indirect=["marc_record"],
)
def test_indicator2(marc_record: Record, production_function: str) -> None:
    """
    The indicator2 value on a 264 field reveals the function.
    """
    production = lone_element(transform_record(marc_record).data.production)
    assert production.function.label == production_function
    assert production.label == "New York"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="264",
                    indicators=Indicators(" ", ind2),
                    subfields=[
                        Subfield(code="a", value="New York"),
                    ],
                )
            ],
            id=f'264: ind2="{ind2}"',
        )
        for ind2 in [" ", "4"]
    ],
    indirect=["marc_record"],
)
def test_unwanted_indicator2(marc_record: Record) -> None:
    """
    We ignore 264 if its ind2 is blank or 4 (copyright notice)
    """
    assert transform_record(marc_record).data.production == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="264",
                    indicators=Indicators(" ", "1"),
                    subfields=[
                        Subfield(code="a", value="New York"),
                    ],
                ),
                Field(
                    tag="260",
                    indicators=Indicators(" ", " "),
                    subfields=[
                        Subfield(code="a", value="Düsseldorf City"),
                    ],
                ),
            ],
            id="",
        )
    ],
    indirect=["marc_record"],
)
def test_prefer_260(marc_record: Record) -> None:
    """
    We currently ignore the 264 field if there is also a 260 field
    This behaviour needs to be revisited.
    """
    production = lone_element(transform_record(marc_record).data.production)
    assert production.label == "Düsseldorf City"
    assert production.function is None


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="008",
                    data="800121s1979uuuuacafr p o o   0    0engrc",
                ),
                Field(
                    tag=code,
                    indicators=Indicators(" ", "1"),
                    subfields=[
                        Subfield(code="a", value="New York"),
                    ],
                ),
            ]
        )
        for code in ["260", "264"]
    ],
    indirect=["marc_record"],
)
def test_populate_first_production_with_008_dates_if_none_of_its_own(
    marc_record: Record,
) -> None:
    production = lone_element(transform_record(marc_record).data.production)
    assert lone_element(production.places).label == "New York"
    period = lone_element(production.dates)
    assert period.range.label == "1979"
    assert period.range.from_time == "1979-01-01T00:00:00Z"


def test_production_label_trims_trailing_punctuation(marc_record: Record) -> None:
    """Overall production label only trims final punctuation; internal punctuation retained."""
    marc_record.add_field(
        Field(
            tag="260",
            subfields=[
                Subfield(
                    code="a", value="Paris:"
                ),  # internal colon retained in full label
                Subfield(
                    code="b", value="Publisher, Ltd.;"
                ),  # internal semicolon retained
                Subfield(code="c", value="1999."),  # final period trimmed
            ],
        )
    )
    production = lone_element(transform_record(marc_record).data.production)
    assert production.label == "Paris: Publisher, Ltd.; 1999"
    assert lone_element(production.places).label == "Paris"
    assert lone_element(production.agents).label == "Publisher, Ltd"
    assert lone_element(production.dates).label == "1999"


def test_production_manufacture_function_label_cleaned(marc_record: Record) -> None:
    """Manufacture subfields cleaned and function label stable."""
    marc_record.add_field(
        Field(
            tag="260",
            subfields=[
                Subfield(code="a", value="Berlin"),
                Subfield(code="e", value="Munich:"),  # will be added as place
                Subfield(code="f", value="Printer Co.;"),  # agent
                Subfield(code="g", value="2001."),  # date
            ],
        )
    )
    production = lone_element(transform_record(marc_record).data.production)
    assert production.function.label == "Manufacture"
    assert production.places[0].label == "Berlin"
    assert production.places[1].label == "Munich"
    assert lone_element(production.agents).label == "Printer Co.".rstrip(".;: ")
    assert lone_element(production.dates).label == "2001"
