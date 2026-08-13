"""Tests for FOLIO identifier extraction (MARC 999 $i instance UUID, MARC 001 HRID)."""

# mypy: allow-untyped-calls

from datetime import datetime

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from tests.adapters.transformers.conftest import _999_field

INSTANCE_UUID = "20000000-0000-0000-0000-000000000001"


def get_work_builder(
    marc_record: Record,
    last_modified: datetime = datetime(2020, 1, 1),
) -> FolioWorkBuilder:
    return FolioWorkBuilder(marc_record, last_modified=last_modified)


@pytest.mark.parametrize("marc_record", [(_999_field(INSTANCE_UUID),)], indirect=True)
def test_source_identifier_value_from_999(marc_record: Record) -> None:
    assert get_work_builder(marc_record).source_identifier_value == INSTANCE_UUID


def test_source_identifier_value_requires_999_field(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Missing instance uuid field.*"):
        get_work_builder(marc_record)


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="999",
                indicators=Indicators("f", "f"),
                subfields=[Subfield(code="t", value="0")],
            ),
        )
    ],
    indirect=True,
)
def test_source_identifier_value_requires_i_subfield(marc_record: Record) -> None:
    with pytest.raises(ValueError, match="Empty instance uuid field.*"):
        get_work_builder(marc_record)


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="999",
                indicators=Indicators("f", "f"),
                subfields=[Subfield(code="t", value="0")],
            ),
            _999_field(INSTANCE_UUID),
        )
    ],
    indirect=True,
)
def test_source_identifier_value_uses_first_i_subfield(marc_record: Record) -> None:
    assert get_work_builder(marc_record).source_identifier_value == INSTANCE_UUID


@pytest.mark.parametrize("marc_record", [(_999_field(INSTANCE_UUID),)], indirect=True)
def test_other_identifiers_includes_hrid_from_001(marc_record: Record) -> None:
    identifiers = get_work_builder(marc_record).other_identifiers
    hrid_identifiers = [
        i for i in identifiers if i.identifier_type.id == "folio-instance-hrid"
    ]
    assert len(hrid_identifiers) == 1
    assert hrid_identifiers[0].value == "default_id"


@pytest.mark.parametrize("marc_record", [(_999_field(INSTANCE_UUID),)], indirect=True)
def test_other_identifiers_requires_001(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    builder = get_work_builder(marc_record)
    with pytest.raises(ValueError, match="Missing hrid field.*"):
        _ = builder.other_identifiers


@pytest.mark.parametrize("marc_record", [(_999_field(INSTANCE_UUID),)], indirect=True)
def test_other_identifiers_requires_non_empty_001(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    marc_record.add_field(Field(tag="001", data="   "))
    builder = get_work_builder(marc_record)
    with pytest.raises(ValueError, match="Empty hrid field.*"):
        _ = builder.other_identifiers


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            _999_field(INSTANCE_UUID),
            Field(
                tag="035",
                subfields=[Subfield(code="a", value="(Sierra Number)7654321")],
            ),
        )
    ],
    indirect=True,
)
def test_other_identifiers_includes_base_035_extraction(marc_record: Record) -> None:
    """The FOLIO override extends the base 035-derived identifiers rather than
    replacing them."""
    identifiers = get_work_builder(marc_record).other_identifiers
    ids = {i.identifier_type.id for i in identifiers}
    assert ids == {"folio-instance-hrid", "sierra-identifier"}
