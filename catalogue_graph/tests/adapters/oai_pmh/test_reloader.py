"""Tests for the OAI-PMH adapter reloader step.

These tests verify the shared reloader implementation used by all OAI-PMH adapters.
Tests are parameterized to run with both Axiell and FOLIO configurations.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import cast
from unittest.mock import MagicMock

from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.oai_pmh.steps.loader import LoaderRuntime
from adapters.oai_pmh.steps.reloader import ReloaderRuntime, handler
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary
from tests.adapters.oai_pmh.conftest import create_window_row, populate_window_store


def _mock_loader_runtime(window_minutes: int = 15) -> LoaderRuntime:
    """Create a mock LoaderRuntime for testing."""
    return LoaderRuntime.model_construct(
        store=cast(WindowStore, SimpleNamespace()),
        table_client=cast(AdapterStore, SimpleNamespace()),
        oai_client=MagicMock(),
        window_generator=SimpleNamespace(window_minutes=window_minutes),
    )


# ---------------------------------------------------------------------------
# handler tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestHandler:
    """Tests for the reloader handler function."""

    def test_with_no_gaps(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that reloader handles ranges with complete coverage."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    now - timedelta(minutes=30), now - timedelta(minutes=15)
                ),
                create_window_row(now - timedelta(minutes=15), now),
            ],
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=adapter_runtime_config,
        )

        response = handler(
            job_id="test-job",
            window_start=now - timedelta(minutes=30),
            window_end=now,
            runtime=runtime,
        )

        assert response.job_id == "test-job"
        assert response.total_gaps == 0
        assert len(response.gaps_processed) == 0
        assert response.dry_run is False

    def test_with_single_gap(
        self,
        monkeypatch: MonkeyPatch,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that reloader identifies and processes a single gap."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        gap_start = now - timedelta(minutes=30)
        gap_end = now - timedelta(minutes=15)

        store = populate_window_store(
            temporary_window_status_table,
            [create_window_row(now - timedelta(minutes=15), now)],
        )

        # Mock the loader's harvester to avoid actual OAI-PMH calls
        def mock_harvest_range(*args, **kwargs):  # type: ignore[no-untyped-def]
            return [
                WindowSummary.model_validate(
                    {
                        "window_start": gap_start,
                        "window_end": gap_end,
                        "state": "success",
                        "attempts": 1,
                        "record_ids": ["id-1", "id-2"],
                        "last_error": None,
                        "updated_at": gap_end,
                        "tags": {
                            "job_id": "test-job",
                            "changeset_id": "changeset-abc",
                            "record_ids_changed": '["id-1"]',
                        },
                    }
                )
            ]

        monkeypatch.setattr(
            "adapters.oai_pmh.steps.reloader.build_harvester",
            lambda event, runtime: SimpleNamespace(harvest_range=mock_harvest_range),
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=adapter_runtime_config,
        )

        response = handler(
            job_id="test-job",
            window_start=gap_start,
            window_end=now,
            runtime=runtime,
        )

        assert response.job_id == "test-job"
        assert response.total_gaps == 1
        assert len(response.gaps_processed) == 1
        assert response.gaps_processed[0].gap_start == gap_start
        assert response.gaps_processed[0].gap_end == gap_end
        assert response.gaps_processed[0].skipped is False
        assert response.gaps_processed[0].error is None
        assert response.gaps_processed[0].loader_response is not None
        assert response.gaps_processed[0].loader_response.changed_record_count == 1

    def test_with_multiple_gaps(
        self,
        monkeypatch: MonkeyPatch,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that reloader processes multiple gaps sequentially."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)

        # Create windows with two gaps
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    now - timedelta(minutes=45), now - timedelta(minutes=30)
                ),
                # Gap 1: -30 to -20
                create_window_row(
                    now - timedelta(minutes=20), now - timedelta(minutes=10)
                ),
                # Gap 2: -10 to 0
            ],
        )

        call_count = {"count": 0}

        def mock_harvest_range(*args, **kwargs):  # type: ignore[no-untyped-def]
            call_count["count"] += 1
            return [
                {
                    "window_key": "test",
                    "window_start": now - timedelta(minutes=15),
                    "window_end": now,
                    "state": "success",
                    "attempts": 1,
                    "record_ids": [],
                    "tags": {"job_id": "test-job"},
                    "last_error": None,
                }
            ]

        monkeypatch.setattr(
            "adapters.oai_pmh.steps.reloader.build_harvester",
            lambda event, runtime: SimpleNamespace(harvest_range=mock_harvest_range),
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=adapter_runtime_config,
        )

        response = handler(
            job_id="test-job",
            window_start=now - timedelta(minutes=45),
            window_end=now,
            runtime=runtime,
        )

        assert response.total_gaps == 2
        assert len(response.gaps_processed) == 2
        assert call_count["count"] == 2  # Verify sequential processing

    def test_dry_run_mode(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that dry-run mode identifies gaps without processing them."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        gap_start = now - timedelta(minutes=30)
        gap_end = now - timedelta(minutes=15)

        store = populate_window_store(
            temporary_window_status_table,
            [create_window_row(now - timedelta(minutes=15), now)],
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=adapter_runtime_config,
        )

        response = handler(
            job_id="test-job",
            window_start=gap_start,
            window_end=now,
            runtime=runtime,
            dry_run=True,
        )

        assert response.job_id == "test-job"
        assert response.total_gaps == 1
        assert len(response.gaps_processed) == 1
        assert response.gaps_processed[0].gap_start == gap_start
        assert response.gaps_processed[0].gap_end == gap_end
        assert response.gaps_processed[0].skipped is True
        assert response.gaps_processed[0].loader_response is None
        assert response.dry_run is True

    def test_with_error_during_reload(
        self,
        monkeypatch: MonkeyPatch,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that reloader captures and reports errors during gap processing."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        gap_start = now - timedelta(minutes=30)

        store = populate_window_store(temporary_window_status_table, [])

        def mock_harvest_range_error(*args, **kwargs):  # type: ignore[no-untyped-def]
            raise RuntimeError("OAI-PMH endpoint unavailable")

        monkeypatch.setattr(
            "adapters.oai_pmh.steps.reloader.build_harvester",
            lambda event, runtime: SimpleNamespace(
                harvest_range=mock_harvest_range_error
            ),
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=adapter_runtime_config,
        )

        response = handler(
            job_id="test-job",
            window_start=gap_start,
            window_end=now,
            runtime=runtime,
        )

        assert response.total_gaps == 1
        assert len(response.gaps_processed) == 1
        assert response.gaps_processed[0].error is not None
        assert "OAI-PMH endpoint unavailable" in response.gaps_processed[0].error
        assert response.gaps_processed[0].loader_response is None

    def test_constructs_correct_loader_event(
        self,
        monkeypatch: MonkeyPatch,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        oai_metadata_prefix: str,
        oai_set_spec: str | None,
    ) -> None:
        """Test that reloader constructs loader event with correct adapter settings."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        gap_start = now - timedelta(minutes=30)

        store = populate_window_store(temporary_window_status_table, [])

        captured_event = {}

        def mock_build_harvester(event, runtime):  # type: ignore[no-untyped-def]
            captured_event["event"] = event
            return SimpleNamespace(
                harvest_range=lambda *args, **kwargs: [
                    WindowSummary(
                        window_key="test",
                        window_start=gap_start,
                        window_end=now,
                        state="success",
                        attempts=1,
                        record_ids=[],
                        tags={},
                        last_error=None,
                    )
                ]
            )

        monkeypatch.setattr(
            "adapters.oai_pmh.steps.reloader.build_harvester", mock_build_harvester
        )

        runtime = ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(window_minutes=15),
            adapter_config=adapter_runtime_config,
        )

        handler(
            job_id="test-job",
            window_start=gap_start,
            window_end=now,
            runtime=runtime,
        )

        event = captured_event["event"]
        assert event.job_id == "test-job"
        assert event.window.start_time == gap_start
        assert event.window.end_time == now
        assert event.metadata_prefix == oai_metadata_prefix
        assert event.set_spec == oai_set_spec
        assert event.window_minutes == 15
        assert event.max_windows is None  # Should process all windows in gap
