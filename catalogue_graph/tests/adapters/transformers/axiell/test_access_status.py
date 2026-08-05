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


def test_mapped_status_wins_over_closed_until_date() -> None:
    record = make_axiell_record()
    add_506(record, "f", "OPEN")
    add_506(record, "g", "2999-01-01")
    assert extract_access_status(record) == Open


def test_restrictionsapply_maps_to_restricted() -> None:
    record = make_axiell_record()
    add_506(record, "f", "RESTRICTIONSAPPLY")
    assert extract_access_status(record) == Restricted


def test_no_status_with_future_closed_until_is_closed() -> None:
    record = make_axiell_record()
    add_506(record, "g", "2999-01-01")
    assert extract_access_status(record) == Closed


def test_no_status_with_past_closed_until_is_none() -> None:
    record = make_axiell_record()
    add_506(record, "g", "2001-01-01")
    assert extract_access_status(record) is None


def test_unrecognised_status_with_future_closed_until_is_closed() -> None:
    record = make_axiell_record()
    add_506(record, "f", "PRIVATE")
    add_506(record, "g", "2999-01-01")
    assert extract_access_status(record) == Closed
