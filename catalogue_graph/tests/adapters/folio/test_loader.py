"""Tests for the FOLIO adapter loader step."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import cast
from unittest.mock import MagicMock, patch

import pytest
from oai_pmh_client.client import OAIClient
from pyiceberg.table import Table as IcebergTable

from adapters.folio.runtime import FOLIO_CONFIG
from adapters.folio.steps import loader
from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHLoaderResponse
from adapters.oai_pmh.steps import loader as base_loader
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow

FOLIO_NAMESPACE = FOLIO_CONFIG.config.adapter_namespace


class StubOAIClient(OAIClient):
    def __init__(self) -> None:
        pass


def _request(now: datetime | None = None) -> OAIPMHLoaderEvent:
    now = now or datetime.now(tz=UTC)
    return OAIPMHLoaderEvent(
        job_id="job-123",
        window=IncrementalWindow(
            start_time=now - timedelta(minutes=15),
            end_time=now,
        ),
        metadata_prefix="marc21_withholdings",
        set_spec=None,
        max_windows=5,
    )


def _runtime_with(
    *,
    store: WindowStore | None = None,
    table_client: AdapterStore | None = None,
    oai_client: OAIClient | None = None,
) -> base_loader.LoaderRuntime:
    from adapters.utils.window_generator import WindowGenerator

    window_generator = WindowGenerator()

    return base_loader.LoaderRuntime(
        store=cast(WindowStore, store),
        table_client=cast(AdapterStore, table_client),
        oai_client=cast(OAIClient, oai_client or StubOAIClient()),
        window_generator=window_generator,
        adapter_namespace=FOLIO_NAMESPACE,
        adapter_name="folio",
    )


def test_execute_loader_updates_iceberg(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1"],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-1"]',
            },
            "changeset_id": "changeset-123",
        }
    )
    table_client = AdapterStore(temporary_table, default_namespace=FOLIO_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = base_loader.execute_loader(req, runtime=runtime)

        assert isinstance(response, OAIPMHLoaderResponse)
        assert response.changeset_ids == ["changeset-123"]
        assert response.changed_record_count == 1
        assert response.job_id == req.job_id
        assert len(response.summaries) == 1

        mock_harvest.assert_called_once_with(
            start_time=req.window.start_time,
            end_time=req.window.end_time,
            max_windows=req.max_windows,
            reprocess_successful_windows=False,
        )


def test_execute_loader_handles_no_new_records(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    table_client = AdapterStore(temporary_table, default_namespace=FOLIO_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": [],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {"job_id": req.job_id},
            "changeset_id": None,
        }
    )

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = base_loader.execute_loader(req, runtime=runtime)

        assert response.changeset_ids == []
        assert response.changed_record_count == 0
        assert table_client.get_all_records().num_rows == 0


def test_handler_publishes_loader_report_with_folio_type(
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1"],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-1"]',
            },
            "changeset_id": "changeset-123",
        }
    )
    table_client = AdapterStore(temporary_table, default_namespace=FOLIO_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    mock_report = MagicMock()

    with (
        patch.object(WindowHarvestManager, "harvest_range") as mock_harvest,
        patch(
            "adapters.oai_pmh.steps.loader.OAIPMHLoaderReport.from_loader"
        ) as mock_from_loader,
    ):
        mock_harvest.return_value = [summary]
        mock_from_loader.return_value = mock_report

        response = loader.handler(req, runtime=runtime)

    mock_from_loader.assert_called_once_with(req, response, adapter_type="folio")
    mock_report.publish.assert_called_once()
    assert response.changed_record_count == 1


def test_build_runtime_returns_loader_runtime(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that build_runtime returns a properly configured LoaderRuntime."""
    from adapters.oai_pmh.steps.loader import LoaderStepConfig

    def mock_build_adapter_table(use_rest_api_table: bool) -> IcebergTable:
        return temporary_table

    def mock_build_window_store(use_rest_api_table: bool) -> WindowStore:
        return WindowStore(temporary_window_status_table)

    monkeypatch.setattr(
        FOLIO_CONFIG,
        "build_adapter_table",
        lambda use_rest_api_table: mock_build_adapter_table(use_rest_api_table),
    )
    monkeypatch.setattr(
        FOLIO_CONFIG,
        "build_window_store",
        lambda use_rest_api_table: mock_build_window_store(use_rest_api_table),
    )
    monkeypatch.setattr(
        FOLIO_CONFIG,
        "build_oai_client",
        lambda http_client=None: StubOAIClient(),
    )

    config_obj = LoaderStepConfig(use_rest_api_table=False, window_minutes=30)
    runtime = loader.build_runtime(config_obj)

    assert runtime.adapter_name == "folio"
    assert runtime.adapter_namespace == "folio"
    assert runtime.window_generator.window_minutes == 30
