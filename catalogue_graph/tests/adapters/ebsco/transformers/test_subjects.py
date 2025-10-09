from pymarc.record import Record

from adapters.ebsco.transformers.ebsco_to_weco import transform_record


def test_no_subjects(marc_record: Record) -> None:
    assert transform_record(marc_record).subjects == []
