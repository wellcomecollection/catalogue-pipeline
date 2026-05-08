"""Tests covering extraction of the record identifier from MARC field 001."""

# mypy: allow-untyped-calls

from datetime import datetime

import pytest
from pymarc.record import Field, Record

from .marcxml_test_transformer import MarcFieldTransformerForTests


def _transform_id(marc_record: Record) -> str:
    transformer = MarcFieldTransformerForTests()
    work = transformer.transform_record(
        marc_record, source_modified_time=datetime.now()
    )
    return work.state.source_identifier.value


@pytest.mark.parametrize(
    "marc_record", [(Field(tag="001", data="ebs999"),)], indirect=True
)
def test_extract_id_from_001(marc_record: Record) -> None:
    assert _transform_id(marc_record) == "ebs999"


def test_id_is_mandatory(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    with pytest.raises(ValueError, match="Missing id field.*"):
        _transform_id(marc_record)


def test_id_must_not_be_empty(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    marc_record.add_field(Field(tag="001", data="   "))
    with pytest.raises(ValueError, match="Empty id field.*"):
        _transform_id(marc_record)
