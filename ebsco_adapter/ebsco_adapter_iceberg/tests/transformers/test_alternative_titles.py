import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from transformers.ebsco_to_weco import transform_record


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag=tag,
                subfields=[
                    Subfield(
                        code="a",
                        value="Memoirs of Sundry Transactions from the World in the Moon",
                    ),
                ],
            ),
        )
        for tag in ["130", "240", "246"]
    ],
    indirect=True,
    ids=lambda fields: fields[0].tag,
)
def test_single_alternative_title(marc_record: Record) -> None:
    """
    Alternative titles can be found in fields
    130 (Main Entry-Uniform Title),
    240 (Uniform Title),
    and 246 (Varying Form of Title)
    """
    work = transform_record(marc_record)
    assert work.alternative_titles == [
        "Memoirs of Sundry Transactions from the World in the Moon"
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="130",
                subfields=[
                    Subfield(
                        code="a",
                        value="Westminster review (London, England : 1852)",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "6"),
                subfields=[
                    Subfield(
                        code="a",
                        value="Westminster and foreign quarterly review",
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_excludes_caption_titles(marc_record: Record) -> None:
    """
    Caption titles are found in field 246 with a 2nd indicator value of 6.
    These are not wanted.

    Example from s9pmua5p (ebs465184e)
    """
    work = transform_record(marc_record)
    assert work.alternative_titles == ["Westminster review (London, England : 1852)"]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(
                        code="a",
                        value="Twelfth Night",
                    ),
                ],
            ),
            Field(
                tag="130",
                subfields=[
                    Subfield(
                        code="a",
                        value="What You Will",
                    ),
                ],
            ),
            Field(
                tag="240",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="Your Own Thing",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "6"),
                subfields=[
                    Subfield(
                        code="a",
                        value="THIS IS A CAPTION",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="Just One of the Guys",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="Motocrossed",
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_alternative_titles(marc_record: Record) -> None:
    """
    Alternative titles can be found in fields 130, 240, and 246
    130 and 240 are non-repeating, but 246 can repeat.
    """
    work = transform_record(marc_record)
    assert work.alternative_titles == [
        "What You Will",  # 130
        "Your Own Thing",  # 240
        "Just One of the Guys",  # 246
        "Motocrossed",  # 246
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="245",
                subfields=[
                    Subfield(
                        code="a",
                        value="Twelfth Night",
                    ),
                ],
            ),
            Field(
                tag="130",
                subfields=[
                    Subfield(
                        code="a",
                        value="What You Will",
                    ),
                ],
            ),
            Field(
                tag="240",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="What You Will",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="What You Will",
                    ),
                ],
            ),
            Field(
                tag="246",
                indicators=Indicators("0", "0"),
                subfields=[
                    Subfield(
                        code="a",
                        value="Motocrossed",
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_distinct_alternative_titles(marc_record: Record) -> None:
    """
    The source data may contain the same alternative title in multiple
    fields (with subtly different meaning).
    As we condense them down to one field, we discard any duplicates
    """
    work = transform_record(marc_record)
    assert work.alternative_titles == [
        "What You Will",
        "Motocrossed",  # 246
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="130",
                subfields=[
                    Subfield(
                        code="a",
                        value="What You Will",
                    ),
                    Subfield(
                        code="r",
                        value="in G flat Major",
                    ),
                    Subfield(
                        code="l",
                        value="with Ayapeneco subtitles",
                    ),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_construct_alternative_title_from_subfields(marc_record: Record) -> None:
    """
    The title string is constructed from all the subfields
    """
    work = transform_record(marc_record)
    assert work.alternative_titles == [
        "What You Will in G flat Major with Ayapeneco subtitles"
    ]
