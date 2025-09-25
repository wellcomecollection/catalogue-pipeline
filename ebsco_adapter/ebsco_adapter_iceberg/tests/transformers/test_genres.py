from pymarc.record import Record

from transformers.ebsco_to_weco import transform_record


def test_no_genres(marc_record: Record) -> None:
    assert transform_record(marc_record).genres == []
