from typing import Union
import pytest
from pydantic import ValidationError
from pymarc.record import Field, Indicators, Record, Subfield

from utils.ebsco_to_weco import transform


# mypy: allow-untyped-calls


def generate_marc_record(
    *,
    id_value: str = "missing",
    title_field: Union[Field | None] = None,
) -> Record:
    record = Record()
    record.add_field(Field(tag="001", data=id_value))
    title_field = title_field or Field(
        tag="245",
        indicators=Indicators("0", "1"),
        subfields=[Subfield(code="a", value="a title")],
    )
    record.add_field(title_field)
    return record


def test_id() -> None:
    assert (
        transform(generate_marc_record(id_value="ebs999")).source_identifier.value
        == "ebs999"
    )


def test_title_is_mandatory() -> None:
    record = generate_marc_record()
    record.remove_fields("245")
    with pytest.raises(ValidationError):
        transform(record)


def test_title_a_b() -> None:
    """
    The title is most commonly generated from the two subfields a and b

    $a - Title (NR)
    $b - Remainder of title (NR)
    """
    record = generate_marc_record(
        title_field=Field(
            tag="245",
            indicators=Indicators("0", "1"),
            subfields=[
                Subfield(code="a", value="101 Ways to Know If Your Cat Is French:"),
                Subfield(
                    code="b", value="How To Talk to Your Cat About Their Secret Life"
                ),
            ],
        )
    )
    work = transform(record)
    assert (
        work.title
        == "101 Ways to Know If Your Cat Is French: How To Talk to Your Cat About Their Secret Life"
    )


def test_title_a_b_c() -> None:
    """
    Subfield c is also included in the title

    $c - Statement of responsibility, etc. (NR)
    e.g. from y5cb65n3

    """
    record = generate_marc_record(
        title_field=Field(
            tag="245",
            indicators=Indicators("0", "1"),
            subfields=[
                Subfield(code="a", value="BMJ :"),
                Subfield(code="b", value="British medical journal /"),
                Subfield(code="c", value="British Medical Association."),
            ],
        )
    )
    work = transform(record)
    assert work.title == "BMJ : British medical journal / British Medical Association."


def test_title_exclude_h_electronic_resource() -> None:
    """
    subfield h never contains anything of value, but it does sometimes provide
    punctuation to be retained between two fields.

    As seen in this example: j6e4cuhm
    [electronic resource] is to be
    """
    record = generate_marc_record(
        title_field=Field(
            tag="245",
            indicators=Indicators("0", "1"),
            subfields=[
                Subfield(code="a", value="The Oxford and Cambridge magazine"),
                Subfield(code="h", value="[electronic resource] /"),
                Subfield(
                    code="c", value="conducted by members of the two universities."
                ),
            ],
        )
    )
    work = transform(record)
    assert (
        work.title
        == "The Oxford and Cambridge magazine / conducted by members of the two universities."
    )
