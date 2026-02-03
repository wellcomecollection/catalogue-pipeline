from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import cast
from unittest.mock import MagicMock

from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

<<<<<<< HEAD
=======
from adapters.axiell import config
>>>>>>> 01fcd0e45 (also do generic reloader)
from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.axiell.steps import reloader
from adapters.axiell.steps.reloader import AxiellAdapterReloaderConfig
from adapters.oai_pmh.steps.loader import LoaderRuntime
from adapters.oai_pmh.steps.reloader import ReloaderRuntime, handler
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_store import WindowStatusRecord, WindowStore
from adapters.utils.window_summary import WindowSummary


def _window_row(
    start: datetime, end: datetime, state: str = "success"
) -> WindowStatusRecord:
    return WindowStatusRecord(
        window_key=f"{start.isoformat()}_{end.isoformat()}",
        window_start=start,
        window_end=end,
        state=state,
        attempts=1,
        last_error=None,
        record_ids=(),
        updated_at=end,
        tags=None,
    )


def _populate_store(table: IcebergTable, rows: list[WindowStatusRecord]) -> WindowStore:
    store = WindowStore(table)
    for row in rows:
        store.upsert(row)
    return store


def _mock_loader_runtime(window_minutes: int = 15) -> LoaderRuntime:
    return LoaderRuntime.model_construct(
        store=cast(WindowStore, SimpleNamespace()),
        table_client=cast(AdapterStore, SimpleNamespace()),
        oai_client=MagicMock(),
        window_generator=SimpleNamespace(window_minutes=window_minutes),
    )


def test_handler_with_no_gaps(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that reloader handles ranges with complete coverage."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    store = _populate_store(
        temporary_window_status_table,
        [
            _window_row(now - timedelta(minutes=30), now - timedelta(minutes=15)),
            _window_row(now - timedelta(minutes=15), now),
        ],
    )

    runtime = ReloaderRuntime(
        store=store,
        loader_runtime=_mock_loader_runtime(),
        adapter_config=AXIELL_CONFIG,
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


def test_handler_with_single_gap(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that reloader identifies and processes a single gap."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    gap_start = now - timedelta(minutes=30)
    gap_end = now - timedelta(minutes=15)

    store = _populate_store(
        temporary_window_status_table,
        [_window_row(now - timedelta(minutes=15), now)],
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
        adapter_config=AXIELL_CONFIG,
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


def test_handler_with_multiple_gaps(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that reloader processes multiple gaps sequentially."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)

    # Create windows with two gaps
    store = _populate_store(
        temporary_window_status_table,
        [
            _window_row(now - timedelta(minutes=45), now - timedelta(minutes=30)),
            # Gap 1: -30 to -20
            _window_row(now - timedelta(minutes=20), now - timedelta(minutes=10)),
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
        adapter_config=AXIELL_CONFIG,
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


def test_handler_dry_run_mode(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that dry-run mode identifies gaps without processing them."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    gap_start = now - timedelta(minutes=30)
    gap_end = now - timedelta(minutes=15)

    store = _populate_store(
        temporary_window_status_table,
        [_window_row(now - timedelta(minutes=15), now)],
    )

    runtime = ReloaderRuntime(
        store=store,
        loader_runtime=_mock_loader_runtime(),
        adapter_config=AXIELL_CONFIG,
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


def test_handler_with_error_during_reload(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that reloader captures and reports errors during gap processing."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    gap_start = now - timedelta(minutes=30)

    store = _populate_store(temporary_window_status_table, [])

    def mock_harvest_range_error(*args, **kwargs):  # type: ignore[no-untyped-def]
        raise RuntimeError("OAI-PMH endpoint unavailable")

    monkeypatch.setattr(
        "adapters.oai_pmh.steps.reloader.build_harvester",
        lambda event, runtime: SimpleNamespace(harvest_range=mock_harvest_range_error),
    )

    runtime = ReloaderRuntime(
        store=store,
        loader_runtime=_mock_loader_runtime(),
        adapter_config=AXIELL_CONFIG,
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


def test_build_runtime_uses_config(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that build_runtime respects configuration options."""
    # Use a typed dict to avoid mypy inferring a single value type
    from typing import Any

    from adapters.oai_pmh.steps.loader import LoaderStepConfig

    captured_config: dict[str, Any] = {}

    def mock_build_window_store(use_rest_api_table: bool) -> WindowStore:
        captured_config["use_rest_api_table"] = use_rest_api_table
        return WindowStore(temporary_window_status_table)

    def mock_build_loader_runtime(
        runtime_config: Any,
        config_obj: LoaderStepConfig | None = None,
    ) -> LoaderRuntime:
        captured_config["runtime_config"] = runtime_config
        captured_config["loader_config"] = config_obj
        return _mock_loader_runtime(window_minutes=60)

    monkeypatch.setattr(
        "adapters.axiell.runtime.AXIELL_CONFIG.build_window_store",
        lambda use_rest_api_table: mock_build_window_store(use_rest_api_table),
    )
    monkeypatch.setattr(
        "adapters.oai_pmh.steps.reloader._build_loader_runtime",
        mock_build_loader_runtime,
    )

    config_obj = AxiellAdapterReloaderConfig(use_rest_api_table=True, window_minutes=60)
    runtime = reloader.build_runtime(config_obj)

    assert captured_config["use_rest_api_table"] is True
    loader_config = captured_config["loader_config"]
    assert loader_config is not None
    assert loader_config.use_rest_api_table is True
    assert isinstance(runtime, ReloaderRuntime)
    assert runtime.loader_runtime.window_generator.window_minutes == 60
    assert loader_config.window_minutes == 60


def test_handler_constructs_correct_loader_event(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that reloader constructs AxiellAdapterLoaderEvent with correct defaults."""
    from adapters.axiell.config import AXIELL_ADAPTER_CONFIG
    from adapters.axiell.runtime import AxiellRuntimeConfig

    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    gap_start = now - timedelta(minutes=30)

    store = _populate_store(temporary_window_status_table, [])

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

    # Create test config with override values
    test_adapter_config = AXIELL_ADAPTER_CONFIG.model_copy(
        update={
            "oai_metadata_prefix": "oai_test",
            "oai_set_spec": "test_set",
            "window_minutes": 15,
        }
    )

    # Create test runtime with custom config
    test_runtime_config = AxiellRuntimeConfig(config=test_adapter_config)

    runtime = ReloaderRuntime(
        store=store,
        loader_runtime=_mock_loader_runtime(window_minutes=15),
        adapter_config=test_runtime_config,
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
    assert event.metadata_prefix == "oai_test"
    assert event.set_spec == "test_set"
    assert event.window_minutes == 15
    assert event.max_windows is None  # Should process all windows in gap


def test_lambda_handler_deserializes_event(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that lambda_handler correctly deserializes dict event."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)

    store = _populate_store(temporary_window_status_table, [])

    def mock_build_runtime(*args, **kwargs):  # type: ignore[no-untyped-def]
        return ReloaderRuntime(
            store=store,
            loader_runtime=_mock_loader_runtime(),
            adapter_config=AXIELL_CONFIG,
        )

    monkeypatch.setattr(
        "adapters.oai_pmh.steps.reloader.build_runtime", mock_build_runtime
    )

    event = {
        "job_id": "lambda-test",
        "window_start": (now - timedelta(minutes=30)).isoformat(),
        "window_end": now.isoformat(),
        "dry_run": True,
    }

    result = reloader.lambda_handler(event, context=None)

    assert result["job_id"] == "lambda-test"
    assert result["dry_run"] is True
    assert "gaps_processed" in result
