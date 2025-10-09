import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.models.work import DigitalLocation
from adapters.ebsco.transformers.ebsco_to_weco import transform_record

from ..helpers import lone_element


def test_no_holdings(marc_record: Record) -> None:
    assert transform_record(marc_record).holdings == []


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="856",
                subfields=[Subfield(code="z", value="Click Here for access!")],
            ),
        )
    ],
    indirect=True,
)
def test_no_url_no_holdings(marc_record: Record) -> None:
    assert transform_record(marc_record).holdings == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="856",
                    subfields=[
                        sf
                        for sf in [
                            Subfield(code="u", value="https://example.com"),
                            Subfield(code="z", value="Click Here for access!"),
                            Subfield(code="3", value="Full text since time immemorial"),
                        ]
                        if sf.code != code
                    ],
                )
            ],
            id=f"missing code: {code}",
        )
        for code in ["u", "3", "z"]
    ],
    indirect=["marc_record"],
)
def test_incomplete_record_no_holdings(marc_record: Record) -> None:
    assert transform_record(marc_record).holdings == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="856",
                    subfields=[
                        Subfield(code="u", value="I am not a URL"),
                        Subfield(code="z", value="Click Here for access!"),
                        Subfield(code="3", value="Full text since time immemorial"),
                    ],
                )
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_dodgy_url_is_no_holdings(marc_record: Record) -> None:
    assert transform_record(marc_record).holdings == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="856",
                    subfields=[
                        Subfield(code="u", value="https://example.com"),
                        Subfield(code="z", value="Click Here for access!"),
                        Subfield(code="3", value="Full text since time immemorial"),
                    ],
                )
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_single_holdings(marc_record: Record) -> None:
    holdings = lone_element(transform_record(marc_record).holdings)
    assert lone_element(holdings.enumeration) == "Full text since time immemorial"
    assert holdings.location.url == "https://example.com"
    assert holdings.location.link_text == "Click Here for access!"
    assert holdings.location.location_type.id == "online-resource"
    conditions = lone_element(holdings.location.access_conditions)  # TODO: finish this
    assert conditions.method.type == "ViewOnline"
    assert conditions.status.relationship.type == "Resource"


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="856",
                    subfields=[
                        Subfield(code="u", value="https://example.com"),
                        Subfield(code="z", value="x"),
                        Subfield(code="3", value="x"),
                    ],
                ),
                Field(
                    tag="856",
                    subfields=[
                        Subfield(code="u", value="I am not a URL"),
                        Subfield(code="z", value="x"),
                        Subfield(code="3", value="x"),
                    ],
                ),
                Field(
                    tag="856",
                    subfields=[
                        Subfield(code="u", value="https://example.com/something"),
                        Subfield(code="z", value="x"),
                        Subfield(code="3", value="x"),
                    ],
                ),
            ]
        )
    ],
    indirect=["marc_record"],
)
def test_multiple_holdings(marc_record: Record) -> None:
    transformed = transform_record(marc_record)
    assert len(transformed.holdings) == 2
    assert isinstance(transformed.holdings[0].location, DigitalLocation)
    assert isinstance(transformed.holdings[1].location, DigitalLocation)
    assert transformed.holdings[0].location.url == "https://example.com"
    assert transformed.holdings[1].location.url == "https://example.com/something"
