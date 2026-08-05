from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# mypy: allow-untyped-calls


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
    record = make_axiell_record(catalogue_status=status)
    assert isinstance(_transform(record), VisibleSourceWork)


@pytest.mark.parametrize("status", ["draft", "in progress"])
def test_suppressed_statuses_yield_deleted_work(status: str) -> None:
    record = make_axiell_record(catalogue_status=status)
    assert isinstance(_transform(record), DeletedSourceWork)


def test_publish_to_web_no_yields_deleted_work() -> None:
    record = make_axiell_record(publish_to_web="no")
    assert isinstance(_transform(record), DeletedSourceWork)


@pytest.mark.parametrize("marker", ["yes", None, "unexpected"])
def test_publish_to_web_without_explicit_no_yields_visible_work(
    marker: str | None,
) -> None:
    """Only an explicit 'no' suppresses: absent markers (pre-stylesheet
    harvests) and unexpected values must keep their current visibility."""
    record = make_axiell_record(publish_to_web=marker)
    assert isinstance(_transform(record), VisibleSourceWork)


def test_publish_to_web_no_without_ref_no_yields_deleted_work() -> None:
    record = make_axiell_record(publish_to_web="no", ref_no=None)
    assert isinstance(_transform(record), DeletedSourceWork)


def test_missing_status_yields_deleted_work() -> None:
    assert isinstance(
        _transform(make_axiell_record(catalogue_status=None)), DeletedSourceWork
    )


@pytest.mark.parametrize("status", ["draft", "in progress", None])
def test_suppressed_record_without_ref_no_yields_deleted_work(
    status: str | None,
) -> None:
    """Cataloguers create records before assigning a RefNo; suppression must not fail on them."""
    record = make_axiell_record(catalogue_status=status, ref_no=None)
    assert isinstance(_transform(record), DeletedSourceWork)


def test_catalogued_record_without_ref_no_raises() -> None:
    record = make_axiell_record(catalogue_status="catalogued", ref_no=None)
    with pytest.raises(ValueError, match="Missing RefNo"):
        _transform(record)


def test_amsg_alt_ref_no_suppresses_regardless_of_status() -> None:
    record = make_axiell_record()
    record = _with_alt_ref_no(record, "AMSG-Research-Guide-001")
    assert isinstance(_transform(record), DeletedSourceWork)


def test_non_amsg_alt_ref_no_does_not_suppress() -> None:
    record = make_axiell_record()
    record = _with_alt_ref_no(record, "PP/ABC/1")
    assert isinstance(_transform(record), VisibleSourceWork)
