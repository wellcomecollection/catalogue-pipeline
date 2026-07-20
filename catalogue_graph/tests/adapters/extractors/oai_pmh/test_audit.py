"""Tests for the OAI-PMH adapter audit step."""

from __future__ import annotations

from typing import cast
from unittest.mock import MagicMock, patch

from adapters.extractors.oai_pmh.reporting import OAIPMHAuditReport
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.steps.oai_pmh import audit
from adapters.steps.oai_pmh.audit import AuditRuntime
from adapters.utils.adapter_store import AdapterStore
from models.incremental_window import IncrementalWindow


def _window() -> IncrementalWindow:
    from datetime import UTC, datetime

    now = datetime(2026, 7, 8, tzinfo=UTC)
    return IncrementalWindow(start_time=now, end_time=now)


def _runtime(
    *, server_count: int | None, harvested_count: int, source_ids: list[str] | None
) -> AuditRuntime:
    config = MagicMock(spec=OAIPMHRuntimeConfig)
    config.source_of_truth_count.return_value = server_count
    config.enumerate_source_ids.return_value = (
        iter(source_ids) if source_ids is not None else None
    )
    store = MagicMock(spec=AdapterStore)
    store.count_active_namespace_records.return_value = harvested_count
    return AuditRuntime(config=config, store=store, adapter_name="axiell")


# --- report ---------------------------------------------------------------


def test_report_drift_is_positive_when_source_ahead() -> None:
    report = OAIPMHAuditReport.from_counts(
        window=_window(),
        adapter_type="axiell",
        server_count=221444,
        harvested_count=220970,
    )
    assert report.drift_count == 474
    assert {m.name: m.value for m in report.metrics} == {
        "server_count": 221444,
        "harvested_count": 220970,
        "drift_count": 474,
    }


def test_report_drift_clamped_to_zero_when_store_ahead() -> None:
    # The audit is one-directional: a store holding more than the source counts
    # (e.g. an unpropagated deletion) is not "drift" this metric reports on.
    report = OAIPMHAuditReport.from_counts(
        window=_window(),
        adapter_type="axiell",
        server_count=100,
        harvested_count=105,
    )
    assert report.drift_count == 0


def test_report_drift_zero_when_source_count_unknown() -> None:
    report = OAIPMHAuditReport.from_counts(
        window=_window(),
        adapter_type="axiell",
        server_count=None,
        harvested_count=220970,
    )
    assert report.drift_count == 0
    assert {m.name: m.value for m in report.metrics}["server_count"] == 0


# --- handler --------------------------------------------------------------


def test_handler_publishes_metric_and_returns_drift() -> None:
    runtime = _runtime(server_count=221444, harvested_count=220970, source_ids=None)
    with patch.object(OAIPMHAuditReport, "publish") as mock_publish:
        response = audit.handler(runtime)
    mock_publish.assert_called_once()
    assert response.drift_count == 474
    assert response.missing_id_sample == []


def test_handler_samples_missing_ids_on_drift_when_enumeration_supported() -> None:
    runtime = _runtime(
        server_count=4,
        harvested_count=2,
        source_ids=["collect:1", "collect:2", "collect:3", "collect:4"],
    )
    have_batch = MagicMock()
    have_batch.column.return_value.to_pylist.return_value = ["collect:1", "collect:2"]
    cast(MagicMock, runtime.store.stream_active_namespace_records).return_value = [
        have_batch
    ]
    with patch.object(OAIPMHAuditReport, "publish"):
        response = audit.handler(runtime, list_missing=True)
    assert response.drift_count == 2
    assert response.missing_id_sample == ["collect:3", "collect:4"]


def test_handler_skips_sampling_when_no_drift() -> None:
    runtime = _runtime(
        server_count=2, harvested_count=2, source_ids=["collect:1", "collect:2"]
    )
    with patch.object(OAIPMHAuditReport, "publish"):
        response = audit.handler(runtime, list_missing=True)
    assert response.drift_count == 0
    assert response.missing_id_sample == []
    cast(MagicMock, runtime.config.enumerate_source_ids).assert_not_called()
