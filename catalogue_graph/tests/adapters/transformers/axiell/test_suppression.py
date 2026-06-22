from datetime import datetime

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# mypy: allow-untyped-calls


def _with_status(record: Record, status: str) -> Record:
    record.add_field(
        Field(
            tag="583",
            indicators=Indicators("0", " "),
            subfields=[Subfield(code="l", value=status)],
        )
    )
    return record


def _with_alt_ref_no(record: Record, alt_ref_no: str) -> Record:
    record.add_field(
        Field(
            tag="035",
            subfields=[Subfield(code="a", value=f"(AltRefNo){alt_ref_no}")],
        )
    )
    return record


def _transform(record: Record) -> VisibleSourceWork | DeletedSourceWork:
    return AxiellWorkBuilder(
        record, last_modified=datetime(2020, 1, 1)
    ).transform_work()


@pytest.mark.parametrize(
    "status", ["catalogued", "partially complete", "Catalogued", "CATALOGUED"]
)
def test_non_suppressed_statuses_yield_visible_work(status: str) -> None:
    record = _with_status(make_axiell_record(), status)
    assert isinstance(_transform(record), VisibleSourceWork)


@pytest.mark.parametrize("status", ["draft", "in progress"])
def test_suppressed_statuses_yield_deleted_work(status: str) -> None:
    record = _with_status(make_axiell_record(), status)
    assert isinstance(_transform(record), DeletedSourceWork)


def test_missing_status_yields_deleted_work() -> None:
    assert isinstance(_transform(make_axiell_record()), DeletedSourceWork)


def test_amsg_alt_ref_no_suppresses_regardless_of_status() -> None:
    record = _with_status(make_axiell_record(), "catalogued")
    record = _with_alt_ref_no(record, "AMSG-Research-Guide-001")
    assert isinstance(_transform(record), DeletedSourceWork)


def test_non_amsg_alt_ref_no_does_not_suppress() -> None:
    record = _with_status(make_axiell_record(), "catalogued")
    record = _with_alt_ref_no(record, "PP/ABC/1")
    assert isinstance(_transform(record), VisibleSourceWork)
