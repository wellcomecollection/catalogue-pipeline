from pymarc.record import Field, Record, Subfield

from adapters.transformers.axiell.access_status import extract_access_status
from models.pipeline.access_status import Closed, Open, Restricted
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# mypy: allow-untyped-calls


def add_506(record: Record, code: str, value: str) -> None:
    record.add_field(Field(tag="506", subfields=[Subfield(code=code, value=value)]))


def test_closed_status_maps_to_closed_without_closed_until_date() -> None:
    # Permanently closed material carries CLOSED with no 506 $g at all.
    record = make_axiell_record()
    add_506(record, "f", "CLOSED")
    assert extract_access_status(record) == Closed


def test_closed_until_date_does_not_affect_the_status() -> None:
    # 506 $g holds the restricted-until or closed-until date. It is the note text
    # that reads it; the status comes from $f alone.
    record = make_axiell_record()
    add_506(record, "f", "OPEN")
    add_506(record, "g", "2999-01-01")
    assert extract_access_status(record) == Open


def test_restrictionsapply_maps_to_restricted() -> None:
    record = make_axiell_record()
    add_506(record, "f", "RESTRICTIONSAPPLY")
    assert extract_access_status(record) == Restricted


def test_no_access_status_field_maps_to_none() -> None:
    # 506 $f is the only source of access status. A record without one has none,
    # whatever else the 506 field carries.
    record = make_axiell_record()
    assert extract_access_status(record) is None
