import pytest
from pymarc.record import Field, Record, Subfield

from adapters.transformers.marc.physical_description import extract_physical_description


def test_no_physical_description_when_no_300_field(marc_record: Record) -> None:
    assert extract_physical_description(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="300",
                subfields=[
                    Subfield(
                        code="b", value="Queuing quokkas quarrel about Queen Quince"
                    ),
                    Subfield(code="d", value="The edifying extent of early emus"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_transforms_subfield_b(marc_record: Record) -> None:
    """Only subfields a, b, c, e are used; other subfields (e.g. $d) are ignored."""
    assert (
        extract_physical_description(marc_record)
        == "Queuing quokkas quarrel about Queen Quince"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="300",
                subfields=[
                    Subfield(code="b", value="The queer quolls quits and quarrels")
                ],
            ),
            Field(
                tag="300",
                subfields=[
                    Subfield(code="b", value="A quintessential quadraped is quick"),
                    Subfield(
                        code="d", value="Egad!  An early eagle is eating the earwig."
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_300_fields_joined_with_br(marc_record: Record) -> None:
    """Multiple 300 fields are joined with <br/>."""
    assert (
        extract_physical_description(marc_record)
        == "The queer quolls quits and quarrels<br/>A quintessential quadraped is quick"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="300",
                subfields=[
                    Subfield(code="a", value="1 videocassette (VHS) (1 min.) :"),
                    Subfield(code="b", value="sound, color, PAL."),
                ],
            ),
            Field(
                tag="300",
                subfields=[
                    Subfield(code="a", value="1 DVD (1 min.) :"),
                    Subfield(code="b", value="sound, color"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_300_fields_with_a_and_b(marc_record: Record) -> None:
    """Subfields within each 300 field are space-joined; fields are joined with <br/>."""
    assert (
        extract_physical_description(marc_record)
        == "1 videocassette (VHS) (1 min.) : sound, color, PAL.<br/>1 DVD (1 min.) : sound, color"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="300",
                subfields=[
                    Subfield(code="a", value="The queer quolls quits and quarrels"),
                    Subfield(code="b", value="A quintessential quadraped is quick"),
                    Subfield(code="c", value="The edifying extent of early emus"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_subfields_a_b_c_joined_with_space(marc_record: Record) -> None:
    """Subfields $a, $b and $c within a single field are joined with a space."""
    assert (
        extract_physical_description(marc_record)
        == "The queer quolls quits and quarrels A quintessential quadraped is quick The edifying extent of early emus"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="300",
                subfields=[
                    Subfield(code="a", value="1 photograph :"),
                    Subfield(code="b", value="photonegative, glass ;"),
                    Subfield(code="c", value="glass 10.6 x 8 cm +"),
                    Subfield(code="e", value="envelope"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_subfields_a_b_c_e_joined_with_space(marc_record: Record) -> None:
    """Subfields $a, $b, $c and $e within a single field are all joined with a space."""
    assert (
        extract_physical_description(marc_record)
        == "1 photograph : photonegative, glass ; glass 10.6 x 8 cm + envelope"
    )
