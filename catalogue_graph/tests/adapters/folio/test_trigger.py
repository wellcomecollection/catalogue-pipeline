"""Tests for the FOLIO adapter trigger step."""

from __future__ import annotations

import random
from datetime import UTC, datetime, timedelta

import pytest
from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

from adapters.folio import config
from adapters.folio.runtime import FOLIO_CONFIG
from adapters.folio.steps import trigger
from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHTriggerEvent
from adapters.oai_pmh.steps.trigger import TriggerRuntime
from adapters.utils.window_store import WindowStatusRecord, WindowStore
from models.incremental_window import IncrementalWindow
from utils.logger import ExecutionContext


def _window_row(start: datetime, end: datetime) -> WindowStatusRecord:
    return WindowStatusRecord(
        window_key=f"{start.isoformat()}_{end.isoformat()}",
        window_start=start,
        window_end=end,
        state="success",
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


def test_build_window_request_uses_lookback_when_no_history(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    monkeypatch.setattr(config, "WINDOW_LOOKBACK_DAYS", 1)
    store = _populate_store(temporary_window_status_table, [])

    request = trigger.build_window_request(store=store, now=now)

    assert request.window.start_time == now - timedelta(days=1)
    assert request.window.end_time == now
    assert request.set_spec == config.OAI_SET_SPEC  # None for FOLIO
    assert request.metadata_prefix == config.OAI_METADATA_PREFIX  # marc21_withholdings
    assert request.job_id == "20251117T1200"


def test_build_window_request_respects_custom_lookback(
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])

    request = trigger.build_window_request(
        store=store,
        now=now,
        window_lookback_days=3,
    )

    assert request.window.start_time == now - timedelta(days=3)
    assert request.window.end_time == now


def test_build_window_request_embeds_window_minutes(
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])

    request = trigger.build_window_request(
        store=store,
        now=now,
        window_minutes=42,
    )

    assert request.window_minutes == 42


def test_build_window_request_respects_last_success(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
    minutes_ago = random.randint(2, 40)
    last_success_end = now - timedelta(minutes=minutes_ago)
    store = _populate_store(
        temporary_window_status_table,
        [
            _window_row(
                last_success_end - timedelta(minutes=minutes_ago), last_success_end
            )
        ],
    )

    request = trigger.build_window_request(store=store, now=now)

    assert request.window.start_time == last_success_end
    assert request.window.end_time == now


def test_build_window_request_errors_when_lag_exceeds_limit(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = _populate_store(
        temporary_window_status_table,
        [_window_row(old_end - timedelta(minutes=random.randint(2, 40)), old_end)],
    )
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    with pytest.raises(RuntimeError):
        trigger.build_window_request(store=store, now=now)


def test_build_window_request_can_skip_lag_enforcement(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = _populate_store(
        temporary_window_status_table,
        [_window_row(old_end - timedelta(minutes=random.randint(2, 40)), old_end)],
    )
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    request = trigger.build_window_request(store=store, now=now, enforce_lag=False)

    assert request.window.start_time == old_end
    assert request.window.end_time == now


def test_lambda_handler_uses_rest_api_table_by_default(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    stub_store = _populate_store(temporary_window_status_table, [])
    captured: dict[str, bool] = {}

    def fake_build_window_store(*, use_rest_api_table: bool) -> WindowStore:
        captured["flag"] = use_rest_api_table
        return stub_store

    def fake_handler(
        event: OAIPMHTriggerEvent,
        runtime: TriggerRuntime,
        execution_context: ExecutionContext | None = None,
        *,
        enforce_lag: bool = True,
    ) -> OAIPMHLoaderEvent:
        assert runtime.store is stub_store
        assert enforce_lag is True
        now = event.now or datetime.now(tz=UTC)
        return OAIPMHLoaderEvent(
            job_id=event.job_id,
            window=IncrementalWindow(
                start_time=now - timedelta(minutes=random.randint(2, 40)),
                end_time=now,
            ),
            metadata_prefix="oai",
            set_spec=None,
        )

    monkeypatch.setattr(FOLIO_CONFIG, "build_window_store", fake_build_window_store)
    monkeypatch.setattr(trigger, "handler", fake_handler)

    trigger.lambda_handler({"time": "2025-11-17T12:00:00Z"}, context=None)

    assert captured["flag"] is True


def test_build_window_request_uses_folio_metadata_prefix(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that FOLIO adapter uses marc21_withholdings metadata prefix."""
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])

    request = trigger.build_window_request(store=store, now=now)

    # FOLIO uses marc21_withholdings
    assert request.metadata_prefix == "marc21_withholdings"
    # FOLIO has no set spec by default
    assert request.set_spec is None
