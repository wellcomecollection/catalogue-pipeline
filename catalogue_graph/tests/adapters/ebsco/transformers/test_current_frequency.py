import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record


def test_no_frequency(marc_record: Record) -> None:
    assert transform_record(marc_record).data.current_frequency is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[Subfield(code="a", value="Samhain")],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_a(marc_record: Record) -> None:
    assert transform_record(marc_record).data.current_frequency == "Samhain"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[Subfield(code="b", value="&lt;Sept. 1991-&gt;")],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_b(marc_record: Record) -> None:
    assert transform_record(marc_record).data.current_frequency == "&lt;Sept. 1991-&gt;"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Every lunar month"),
                    Subfield(code="b", value="Magáksicaagli Wí 1984"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_a_b(marc_record: Record) -> None:
    assert (
        transform_record(marc_record).data.current_frequency
        == "Every lunar month Magáksicaagli Wí 1984"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Every lunar month"),
                    Subfield(code="b", value="Magáksicaagli Wí 1984 - 2002"),
                ],
            ),
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Imbolc and Lammas"),
                    Subfield(code="b", value="1666 -"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_multiple(marc_record: Record) -> None:
    assert (
        transform_record(marc_record).data.current_frequency
        == "Every lunar month Magáksicaagli Wí 1984 - 2002 Imbolc and Lammas 1666 -"
    )
