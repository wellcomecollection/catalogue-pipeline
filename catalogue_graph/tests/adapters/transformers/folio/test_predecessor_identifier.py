"""Tests for FOLIO predecessor identifier extraction (MARC 907 $a → Sierra system number)."""

# mypy: allow-untyped-calls

from contextlib import suppress
from datetime import datetime

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.folio_record_transformer import FolioRecordTransformer


def get_record_transformer(
    marc_record: Record, last_modified: datetime = datetime(2020, 1, 1)
) -> FolioRecordTransformer:
    return FolioRecordTransformer(marc_record, last_modified=last_modified)


def _907_field(value: str) -> Field:
    return Field(
        tag="907",
        indicators=Indicators(" ", " "),
        subfields=[Subfield(code="a", value=value)],
    )


@pytest.fixture
def marc_record(request: pytest.FixtureRequest) -> Record:
    record = Record()
    with suppress(AttributeError):
        record.add_field(*request.param)
    record.add_field(Field(tag="001", data="default_id"))
    return record


@pytest.mark.parametrize(
    "marc_record,expected",
    [
        ((_907_field("b12345679"),), "b12345679"),
        ((_907_field("b1234567x"),), "b1234567x"),
        ((_907_field(".b12345679"),), "b12345679"),
    ],
    indirect=["marc_record"],
)
def test_extracts_predecessor_id_from_907(marc_record: Record, expected: str) -> None:
    identifier = get_record_transformer(marc_record).predecessor_identifier
    assert identifier is not None
    assert identifier.value == expected


def test_returns_none_when_no_907(marc_record: Record) -> None:
    assert get_record_transformer(marc_record).predecessor_identifier is None


@pytest.mark.parametrize(
    "marc_record",
    [(_907_field("b12345679"), _907_field("b12345679"))],
    indirect=True,
)
def test_deduplicates_identical_907_fields(marc_record: Record) -> None:
    identifier = get_record_transformer(marc_record).predecessor_identifier
    assert identifier is not None
    assert identifier.value == "b12345679"


@pytest.mark.parametrize(
    "marc_record",
    [(_907_field("b12345679"), _907_field("b99999990"))],
    indirect=True,
)
def test_raises_when_multiple_distinct_907_values(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Multiple distinct instances of varfield"):
        _ = get_record_transformer(marc_record).predecessor_identifier


@pytest.mark.parametrize(
    "marc_record,value",
    [
        ((_907_field("1234567"),), "1234567"),
        ((_907_field("b123456"),), "b123456"),
        ((_907_field("b1234567"),), "b1234567"),
        ((_907_field("b123456789"),), "b123456789"),
        ((_907_field("x12345679"),), "x12345679"),
    ],
    indirect=["marc_record"],
)
def test_raises_for_invalid_sierra_system_number(
    marc_record: Record, value: str
) -> None:
    with pytest.raises(ValueError, match="does not match Sierra system number format"):
        _ = get_record_transformer(marc_record).predecessor_identifier
