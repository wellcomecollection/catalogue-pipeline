"""Tests covering the extraction of data from field 245 into a title string.

https://www.loc.gov/marc/bibliographic/bd245.html

These tests exercise the MARC title extractor via a minimal MarcXmlTransformer
subclass used for unit testing MARC field transformers.
"""

from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from .marcxml_test_transformer import MarcFieldTransformerForTests


def _transform_title(marc_record: Record) -> str:
    transformer = MarcFieldTransformerForTests()
    work = transformer.transform_record(
        marc_record, source_modified_time=datetime.now()
    )
    assert work.data.title is not None
    return work.data.title


def test_title_is_mandatory(marc_record: Record) -> None:
    marc_record.remove_fields("245")
    with pytest.raises(ValueError, match="Missing title field.*"):
        _transform_title(marc_record)


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
        _transform_title(marc_record)


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
    """A minimal title uses only the $a subfield ($a - Title)."""
    assert _transform_title(marc_record) == "How to Avoid Huge Ships"


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
    """The title is most commonly generated from the $a and $b subfields."""
    assert (
        _transform_title(marc_record)
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
    """Subfield $c is also included in the title."""
    assert (
        _transform_title(marc_record)
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
    """Remove '[electronic resource]' from $h but retain punctuation where applicable."""
    assert (
        _transform_title(marc_record)
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
    """Subfields $n and $p are also included in the title."""
    assert (
        _transform_title(marc_record)
        == "Philosophical transactions of the Royal Society of London. Series B, Biological sciences"
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
                        value=(
                            "Serious advice to students and young ministers. A sermon preached at Broad-Mead, Bristol, before the Education-Society, August 17, 1774, And published at their Request. By John Tommas"
                        ),
                    ),
                    Subfield(code="h", value="[electronic resource]."),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_trailing_h_removed(marc_record: Record) -> None:
    """Trailing $h subfield is dropped entirely (Scala parity)."""
    assert (
        _transform_title(marc_record)
        == "Serious advice to students and young ministers. A sermon preached at Broad-Mead, Bristol, before the Education-Society, August 17, 1774, And published at their Request. By John Tommas"
    )
