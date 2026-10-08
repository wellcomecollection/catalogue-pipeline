from datetime import datetime
from unittest.mock import MagicMock

import pytest
from pymarc.record import Field, Record, Subfield

import adapters.transformers.builders.axiell_work_builder as axiell_work_builder
from adapters.extractors.oai_pmh.axiell import config as axiell_config
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork
from tests.adapters.transformers.axiell.conftest import make_axiell_record
from tests.adapters.transformers.conftest import _907_field

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


@pytest.mark.parametrize("marker", ["yes", "Yes", "YES"])
def test_publish_to_web_yes_yields_visible_work(marker: str) -> None:
    record = make_axiell_record(publish_to_web=marker)
    assert isinstance(_transform(record), VisibleSourceWork)


@pytest.mark.parametrize("marker", ["no", None, "unexpected"])
def test_publish_to_web_without_explicit_yes_yields_deleted_work(
    marker: str | None,
) -> None:
    """The stylesheet emits the marker on every record, so absence fails closed."""
    record = make_axiell_record(publish_to_web=marker)
    assert isinstance(_transform(record), DeletedSourceWork)


@pytest.mark.parametrize(
    "status", ["catalogued", "draft", "partially complete", "in progress", None]
)
def test_catalogue_status_does_not_affect_visible_work(status: str | None) -> None:
    """publish_to_web is the sole publish authority; status plays no part."""
    record = make_axiell_record(catalogue_status=status, publish_to_web="yes")
    assert isinstance(_transform(record), VisibleSourceWork)


@pytest.mark.parametrize(
    "status", ["catalogued", "draft", "partially complete", "in progress", None]
)
def test_catalogue_status_does_not_rescue_suppressed_work(status: str | None) -> None:
    record = make_axiell_record(catalogue_status=status, publish_to_web="no")
    assert isinstance(_transform(record), DeletedSourceWork)


@pytest.mark.parametrize("marker", [None, "unexpected"])
def test_anomalous_marker_suppression_logs_warning(
    marker: str | None, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A missing/unrecognised marker is an anomaly; it must not suppress silently."""
    mock_logger = MagicMock()
    monkeypatch.setattr(axiell_work_builder, "logger", mock_logger)
    record = make_axiell_record(publish_to_web=marker)
    assert isinstance(_transform(record), DeletedSourceWork)
    mock_logger.warning.assert_called_once()


def test_explicit_no_suppression_does_not_log(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    mock_logger = MagicMock()
    monkeypatch.setattr(axiell_work_builder, "logger", mock_logger)
    record = make_axiell_record(publish_to_web="no")
    assert isinstance(_transform(record), DeletedSourceWork)
    mock_logger.warning.assert_not_called()


@pytest.mark.parametrize("source", ["refno", "part_of"])
@pytest.mark.parametrize("marker", ["no", None])
def test_suppressed_record_without_ref_no_yields_deleted_work(
    marker: str | None, source: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Cataloguers create records before assigning a RefNo; suppression must not fail on them."""
    monkeypatch.setattr(axiell_config, "AXIELL_COLLECTION_PATH_SOURCE", source)
    record = make_axiell_record(publish_to_web=marker, ref_no=None)
    assert isinstance(_transform(record), DeletedSourceWork)


def test_publishable_record_without_ref_no_raises_in_refno_mode(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(axiell_config, "AXIELL_COLLECTION_PATH_SOURCE", "refno")
    record = make_axiell_record(publish_to_web="yes", ref_no=None)
    with pytest.raises(ValueError, match="Missing RefNo"):
        _transform(record)


def test_publishable_record_without_ref_no_is_visible_in_part_of_mode(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(axiell_config, "AXIELL_COLLECTION_PATH_SOURCE", "part_of")
    record = make_axiell_record(publish_to_web="yes", ref_no=None, part_of="PP/ABC")
    record = _with_alt_ref_no(record, "PP/ABC/1")
    assert isinstance(_transform(record), VisibleSourceWork)


def test_amsg_alt_ref_no_suppresses_record_without_ref_no_in_part_of_mode(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(axiell_config, "AXIELL_COLLECTION_PATH_SOURCE", "part_of")
    record = make_axiell_record(publish_to_web="yes", ref_no=None)
    record = _with_alt_ref_no(record, "AMSG-Research-Guide-001")
    assert isinstance(_transform(record), DeletedSourceWork)


def test_amsg_alt_ref_no_suppresses_publishable_record() -> None:
    record = make_axiell_record(publish_to_web="yes")
    record = _with_alt_ref_no(record, "AMSG-Research-Guide-001")
    assert isinstance(_transform(record), DeletedSourceWork)


def test_non_amsg_alt_ref_no_does_not_suppress() -> None:
    record = make_axiell_record(publish_to_web="yes")
    record = _with_alt_ref_no(record, "PP/ABC/1")
    assert isinstance(_transform(record), VisibleSourceWork)


CALM_ID = "10000000-0000-4000-8000-000000000001"
OTHER_CALM_ID = "20000000-0000-4000-8000-000000000002"


def test_suppressed_work_carries_calm_predecessor() -> None:
    """Without the predecessor, a record first seen suppressed is minted a fresh id."""
    record = make_axiell_record(publish_to_web="no")
    record.add_field(_907_field(CALM_ID))
    work = _transform(record)
    assert isinstance(work, DeletedSourceWork)
    assert work.state.predecessor_identifier is not None
    assert work.state.predecessor_identifier.identifier_type.id == "calm-record-id"
    assert work.state.predecessor_identifier.value == CALM_ID


def test_suppressed_work_without_907_has_no_predecessor() -> None:
    work = _transform(make_axiell_record(publish_to_web="no"))
    assert isinstance(work, DeletedSourceWork)
    assert work.state.predecessor_identifier is None


def test_suppressed_work_with_conflicting_907_values_raises() -> None:
    record = make_axiell_record(publish_to_web="no")
    record.add_field(_907_field(CALM_ID))
    record.add_field(_907_field(OTHER_CALM_ID))
    with pytest.raises(ValueError, match="Multiple distinct instances of varfield"):
        _transform(record)
