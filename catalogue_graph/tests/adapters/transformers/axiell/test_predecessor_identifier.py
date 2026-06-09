from datetime import datetime

import pytest
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from pymarc.record import Record

from tests.adapters.transformers.conftest import _907_field

VALID_UUID = "f1fab6a1-b172-418f-93eb-bc24740e266d"
ANOTHER_VALID_UUID = "2637bb63-9ffa-4a51-93d9-be35038d39f9"


def get_work_builder(
    marc_record: Record,
    last_modified: datetime = datetime(2020, 1, 1),
) -> AxiellWorkBuilder:
    return AxiellWorkBuilder(marc_record, last_modified=last_modified)


@pytest.mark.parametrize(
    "marc_record,expected",
    [
        ((_907_field(VALID_UUID),), VALID_UUID),
        ((_907_field(f".{ANOTHER_VALID_UUID}"),), ANOTHER_VALID_UUID),
    ],
    indirect=["marc_record"],
)
def test_extracts_predecessor_id_from_907(marc_record: Record, expected: str) -> None:
    identifier = get_work_builder(marc_record).predecessor_identifier
    assert identifier is not None
    assert identifier.value == expected


def test_returns_none_when_no_907(marc_record: Record) -> None:
    assert get_work_builder(marc_record).predecessor_identifier is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (_907_field(VALID_UUID), _907_field(VALID_UUID))
    ],
    indirect=["marc_record"],
)
def test_deduplicates_identical_907_fields(marc_record: Record) -> None:
    identifier = get_work_builder(marc_record).predecessor_identifier
    assert identifier is not None
    assert identifier.value == VALID_UUID


@pytest.mark.parametrize(
    "marc_record",
    [(_907_field(VALID_UUID), _907_field(ANOTHER_VALID_UUID))],
    indirect=True,
)
def test_raises_when_multiple_distinct_907_values(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Multiple distinct instances of varfield"):
        _ = get_work_builder(marc_record).predecessor_identifier


@pytest.mark.parametrize(
    "marc_record",
    [
        (_907_field("1234567"),),
        (_907_field("2637bb639ffa-4a51-93d9-be35038d39f9"),),
    ],
    indirect=["marc_record"],
)
def test_raises_for_invalid_calm_identifier(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="does not match CALM record ID format"):
        _ = get_work_builder(marc_record).predecessor_identifier
