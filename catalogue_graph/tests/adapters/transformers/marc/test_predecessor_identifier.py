"""Tests covering extraction of the predecessor identifier from MARC field 907."""

# mypy: allow-untyped-calls

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.marc.predecessor_identifier import extract_predecessor_id


def _907_field(value: str) -> Field:
    return Field(
        tag="907",
        indicators=Indicators(" ", " "),
        subfields=[Subfield(code="a", value=value)],
    )


@pytest.mark.parametrize("marc_record", [(_907_field("b1234567"),)], indirect=True)
def test_extracts_predecessor_id_from_907(marc_record: Record) -> None:
    assert extract_predecessor_id(marc_record) == "b1234567"


def test_returns_none_when_no_907(marc_record: Record) -> None:
    assert extract_predecessor_id(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [(_907_field("b1234567"), _907_field("b1234567"))],
    indirect=True,
)
def test_deduplicates_identical_907_fields(marc_record: Record) -> None:
    assert extract_predecessor_id(marc_record) == "b1234567"


@pytest.mark.parametrize(
    "marc_record",
    [(_907_field("b1234567"), _907_field("b9999999"))],
    indirect=True,
)
def test_raises_when_multiple_distinct_907_values(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Multiple predecessor identifiers"):
        extract_predecessor_id(marc_record)
