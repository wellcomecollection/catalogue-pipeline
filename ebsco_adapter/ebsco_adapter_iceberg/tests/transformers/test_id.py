import pytest
from pymarc.record import Field, Record

from transformers.ebsco_to_weco import transform_record

# mypy: allow-untyped-calls


@pytest.mark.parametrize(
    "marc_record", [(Field(tag="001", data="ebs999"),)], indirect=True
)
def test_extract_id_from_001(marc_record: Record) -> None:
    assert transform_record(marc_record).id == "ebs999"


def test_id_is_mandatory(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    with pytest.raises(ValueError, match="Missing id field.*"):
        transform_record(marc_record)


def test_id_must_not_be_empty(marc_record: Record) -> None:
    marc_record.remove_fields("001")
    marc_record.add_field(Field(tag="001", data="   "))
    with pytest.raises(ValueError, match="Empty id field.*"):
        transform_record(marc_record)
