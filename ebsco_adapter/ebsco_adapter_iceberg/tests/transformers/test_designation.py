import pytest
from pymarc.record import Field, Record, Subfield

from transformers.ebsco_to_weco import transform_record
from ..helpers import lone_element


def test_no_designation(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == []


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="362",
                    subfields=[Subfield(code="a", value="    hello, I'm in space!     ")],
                ),
        )
    ],
    indirect=True,
)
def test_tidies_value(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == ["hello, I'm in space!"]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="362",
                    subfields=[
                        Subfield(code="a", value="Tertiary adjunct of unimatrix zero one")
                    ],
                ),
        )
    ],
    indirect=True,
)
def test_extract_designation_from_362(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == [
        "Tertiary adjunct of unimatrix zero one"
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="362",
                    subfields=[Subfield(code="a", value="Seven of Nine")],
                ),
                Field(
                    tag="362",
                    subfields=[
                        Subfield(code="a", value="Tertiary adjunct of unimatrix zero one")
                    ],
                ),
        )
    ],
    indirect=True,
)
def test_multiple_designations(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == [
        "Seven of Nine",
        "Tertiary adjunct of unimatrix zero one",
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="362",
                    subfields=[
                        Subfield(code="a", value="Tertiary adjunct of unimatrix zero one"),
                        Subfield(code="z", value="Memory Alpha"),
                    ],
                ),
        )
    ],
    indirect=True,
)
def test_ignore_subfield_z(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == [
        "Tertiary adjunct of unimatrix zero one"
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
                Field(
                    tag="362",
                    subfields=[Subfield(code="a", value="")],
                ),
                Field(
                    tag="362",
                    subfields=[Subfield(code="a", value="             ")],
                ),
                Field(
                    tag="362",
                    subfields=[Subfield(code="z", value="Bloke down the pub")],
                ),
                Field(
                    tag="362",
                    subfields=[
                        Subfield(code="a", value="This one"),
                        Subfield(code="z", value="Bloke down the pub"),
                    ],
                ),
        )
    ],
    indirect=True,
)
def test_empty_designation_is_no_designation(marc_record: Record) -> None:
    assert transform_record(marc_record).designation == ["This one"]
