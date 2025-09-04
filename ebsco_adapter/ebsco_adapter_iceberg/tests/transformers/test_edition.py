import pytest
from pymarc.record import Field, Record, Subfield

from transformers.ebsco_to_weco import transform_record


def test_no_edition(marc_record: Record) -> None:
    assert transform_record(marc_record).edition is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="")],
            ),
        )
    ],
    indirect=True,
)
def test_empty_edition_is_no_edition(marc_record: Record) -> None:
    assert transform_record(marc_record).edition is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="    hello, I'm in space!     ")],
            ),
        )
    ],
    indirect=True,
)
def test_tidies_value(marc_record: Record) -> None:
    assert transform_record(marc_record).edition == "hello, I'm in space!"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="Édition franc̦aise.")],
            ),
        )
    ],
    indirect=True,
)
def test_extract_edition_from_250(marc_record: Record) -> None:
    assert transform_record(marc_record).edition == "Édition franc̦aise."


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="Édition franc̦aise.")],
            ),
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="Rhifyn Cymraeg")],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_editions(marc_record: Record) -> None:
    assert transform_record(marc_record).edition == "Édition franc̦aise. Rhifyn Cymraeg"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[
                    Subfield(code="a", value="Større utgave"),
                    Subfield(code="7", value="HP"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_ignore_other_subfields(marc_record: Record) -> None:
    assert transform_record(marc_record).edition == "Større utgave"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="")],
            ),
            Field(
                tag="250",
                subfields=[Subfield(code="a", value="             ")],
            ),
            Field(
                tag="250",
                subfields=[Subfield(code="7", value="Bloke down the pub")],
            ),
            Field(
                tag="250",
                subfields=[
                    Subfield(code="a", value="This one"),
                    Subfield(code="7", value="Bloke down the pub"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_empty_editions_are_ignored(marc_record: Record) -> None:
    assert transform_record(marc_record).edition == "This one"
