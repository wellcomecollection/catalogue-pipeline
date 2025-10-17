"""
Tests covering the extraction of data from field 245 into a title string.
https://www.loc.gov/marc/bibliographic/bd245.html
"""

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record


def test_title_is_mandatory(marc_record: Record) -> None:
    marc_record.remove_fields("245")
    with pytest.raises(ValueError, match="Missing title field.*"):
        transform_record(marc_record)


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[Subfield(code="a", value=""), Subfield(code="b", value="")],
            ),
        )
    ],
    indirect=True,
)
def test_title_must_not_be_empty(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Empty title field.*"):
        transform_record(marc_record)


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[Subfield(code="a", value="How to Avoid Huge Ships")],
            ),
        )
    ],
    indirect=True,
)
def test_title_a(marc_record: Record) -> None:
    """
    A minimal title uses only the $a subfield
    $a - Title
    """
    work = transform_record(marc_record)
    assert work.data.title == "How to Avoid Huge Ships"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(code="a", value="101 Ways to Know If Your Cat Is French:"),
                    Subfield(
                        code="b",
                        value="How To Talk to Your Cat About Their Secret Life",
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_title_a_b(marc_record: Record) -> None:
    """
    The title is most commonly generated from the two subfields a and b

    $b - Remainder of title (NR)
    """
    work = transform_record(marc_record)
    assert (
        work.data.title
        == "101 Ways to Know If Your Cat Is French: How To Talk to Your Cat About Their Secret Life"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(code="a", value="BMJ :"),
                    Subfield(code="b", value="British medical journal /"),
                    Subfield(code="c", value="British Medical Association."),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_title_a_b_c(marc_record: Record) -> None:
    """
    Subfield c is also included in the title

    $c - Statement of responsibility, etc. (NR)
    e.g. from y5cb65n3 (ebs100966e)

    """
    work = transform_record(marc_record)
    assert (
        work.data.title
        == "BMJ : British medical journal / British Medical Association."
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(code="a", value="The Oxford and Cambridge magazine"),
                    Subfield(code="h", value="[electronic resource] /"),
                    Subfield(
                        code="c", value="conducted by members of the two universities."
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_exclude_electronic_resource(marc_record: Record) -> None:
    """
    subfield h sometimes provides punctuation to be retained between two fields,
    but also contains the unwanted term [electronic resource]

    As seen in this example: j6e4cuhm (ebs375816e)
    [electronic resource] is to be removed,
    but the h subfield contains punctuation which is to be retained
    """
    work = transform_record(marc_record)
    assert (
        work.data.title
        == "The Oxford and Cambridge magazine / conducted by members of the two universities."
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(
                        code="a",
                        value="Philosophical transactions of the Royal Society of London.",
                    ),
                    Subfield(code="n", value="Series B,"),
                    Subfield(code="p", value="Biological sciences"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_title_a_n_p(marc_record: Record) -> None:
    """
    Subfields n and p are also included in the title

    $n - Number of part/section of a work (R)
    $p - Name of part/section of a work (R)

    e.g. from qs9k7q54 (ebs83382e)
    """
    work = transform_record(marc_record)
    assert (
        work.data.title
        == "Philosophical transactions of the Royal Society of London. Series B, Biological sciences"
    )
