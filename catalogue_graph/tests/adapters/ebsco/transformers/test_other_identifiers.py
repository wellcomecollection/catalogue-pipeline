import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record
from models.pipeline.identifier import Id, SourceIdentifier


def test_no_other_identifiers(marc_record: Record) -> None:
    assert transform_record(marc_record).data.other_identifiers == []


def build_source_identifier(id_type: str, value: str) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=id_type), ontology_type="Work", value=value
    )


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
    assert work.data.other_identifiers == [
        build_source_identifier("isbn", "978-1-890159-02-3")
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
    assert work.data.other_identifiers == [build_source_identifier("issn", "1890-6729")]


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
    assert work.data.other_identifiers == [
        build_source_identifier("isbn", "978-1-890159-02-3"),
        build_source_identifier("issn", "1890-6729"),
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
    assert work.data.other_identifiers == [
        build_source_identifier("issn", "0046-8541"),
        build_source_identifier("isbn", "978-1984857132"),
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
    assert work.data.other_identifiers == []
