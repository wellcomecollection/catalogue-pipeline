"""Tests covering extraction of standard identifiers (ISBN/ISSN) from MARC.

MARC fields:
- 020 (ISBN)
- 022 (ISSN)

Although the extractor currently lives under `adapters.ebsco.transformers.other_identifiers`,
these tests are MARC-field level and can be shared across adapters.
"""

# mypy: allow-untyped-calls

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.other_identifiers import extract_other_identifiers
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.work_data import WorkData
from tests.adapters.marc.marcxml_test_transformer import MarcXmlTransformerForTests


def _transform_other_identifiers(marc_record: Record) -> list[SourceIdentifier]:
    transformer = MarcXmlTransformerForTests(
        build_work_data=lambda r: WorkData(other_identifiers=extract_other_identifiers(r))
    )
    work = transformer.transform_record(marc_record, source_modified_time=datetime.now())
    return work.data.other_identifiers


def build_source_identifier(id_type: str, value: str) -> SourceIdentifier:
    return SourceIdentifier(identifier_type=Id(id=id_type), ontology_type="Work", value=value)


def test_no_other_identifiers(marc_record: Record) -> None:
    assert _transform_other_identifiers(marc_record) == []


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
    assert _transform_other_identifiers(marc_record) == [
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
    assert _transform_other_identifiers(marc_record) == [
        build_source_identifier("issn", "1890-6729")
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
    assert _transform_other_identifiers(marc_record) == [
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
    """If the field includes cancelled/incorrect identifiers, only keep current $a."""
    assert _transform_other_identifiers(marc_record) == [
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
    """If a field has only cancelled/incorrect identifiers, ignore it entirely."""
    assert _transform_other_identifiers(marc_record) == []
