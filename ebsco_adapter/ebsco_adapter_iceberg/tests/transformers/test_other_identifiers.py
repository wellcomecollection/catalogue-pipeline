import pytest
from pymarc.record import Field, Record, Subfield

from models.work import SourceIdentifier
from transformers.ebsco_to_weco import transform_record


def test_no_other_identifiers(marc_record: Record) -> None:
    assert transform_record(marc_record).other_identifiers == []


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="020",
                subfields=[Subfield(code="a", value="978-1-890159-02-3")],
            ),
        )
    ],
    indirect=True,
)
def test_isbn(marc_record: Record) -> None:
    work = transform_record(marc_record)
    assert work.other_identifiers == [
        SourceIdentifier(
            identifier_type="isbn", ontology_type="Work", value="978-1-890159-02-3"
        )
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="022",
                subfields=[Subfield(code="a", value="1890-6729")],
            ),
        )
    ],
    indirect=True,
)
def test_issn(marc_record: Record) -> None:
    work = transform_record(marc_record)
    assert work.other_identifiers == [
        SourceIdentifier(
            identifier_type="issn", ontology_type="Work", value="1890-6729"
        )
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="020",
                subfields=[Subfield(code="a", value="978-1-890159-02-3")],
            ),
            Field(
                tag="022",
                subfields=[Subfield(code="a", value="1890-6729")],
            ),
        )
    ],
    indirect=True,
)
def test_both(marc_record: Record) -> None:
    work = transform_record(marc_record)
    assert work.other_identifiers == [
        SourceIdentifier(
            identifier_type="isbn", ontology_type="Work", value="978-1-890159-02-3"
        ),
        SourceIdentifier(
            identifier_type="issn", ontology_type="Work", value="1890-6729"
        ),
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="022",
                subfields=[
                    Subfield(code="a", value="0046-8541"),
                    Subfield(code="y", value="0000-0000"),
                ],
            ),
            Field(
                tag="020",
                subfields=[
                    Subfield(code="a", value="978-1984857132"),
                    Subfield(code="z", value="000-0-000000-00-0"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_only_take_current_identifiers(marc_record: Record) -> None:
    """
    Sometimes, an ISBN or ISSN field contains a cancelled or incorrect identifier,
    as well as the actual current identifier.  We do not extract the non-current ones.
    """
    work = transform_record(marc_record)
    assert work.other_identifiers == [
        SourceIdentifier(
            identifier_type="issn", ontology_type="Work", value="0046-8541"
        ),
        SourceIdentifier(
            identifier_type="isbn", ontology_type="Work", value="978-1984857132"
        ),
    ]


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="022",
                subfields=[Subfield(code="y", value="1079-5146")],
            ),
            Field(
                tag="020",
                subfields=[Subfield(code="z", value="978-1-906814-4-10")],
            ),
        )
    ],
    indirect=True,
)
def test_ignore_empty_identifiers(marc_record: Record) -> None:
    """
    Sometimes, an ISBN or ISSN field only contains a cancelled or incorrect identifier,
    as we do not extract these, that field is to be completely ignored
    """
    work = transform_record(marc_record)
    assert work.other_identifiers == []
