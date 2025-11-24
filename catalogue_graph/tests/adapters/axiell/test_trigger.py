from __future__ import annotations

import random
from datetime import UTC, datetime, timedelta

import pytest
from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

from adapters.axiell import config
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    AxiellAdapterTriggerEvent,
)
from adapters.axiell.steps import trigger
from adapters.utils.window_store import IcebergWindowStore, WindowStatusRecord


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


def _populate_store(
    table: IcebergTable, rows: list[WindowStatusRecord]
) -> IcebergWindowStore:
    store = IcebergWindowStore(table)
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

    assert request.window_start == now - timedelta(days=1)
    assert request.window_end == now
    assert request.set_spec == config.OAI_SET_SPEC
    assert request.metadata_prefix == config.OAI_METADATA_PREFIX
    assert request.job_id


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

    assert request.window_start == last_success_end
    assert request.window_end == now


def test_build_window_request_finds_latest_among_multiple_windows(
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)

    # Create windows with different end times
    end1 = now - timedelta(minutes=45)
    end2 = now - timedelta(minutes=30)  # This is the latest
    end3 = now - timedelta(minutes=60)

    store = _populate_store(
        temporary_window_status_table,
        [
            _window_row(end1 - timedelta(minutes=random.randint(2, 40)), end1),
            _window_row(end2 - timedelta(minutes=random.randint(2, 40)), end2),
            _window_row(end3 - timedelta(minutes=random.randint(2, 40)), end3),
        ],
    )

    request = trigger.build_window_request(store=store, now=now)

    assert request.window_start == end2
    assert request.window_end == now


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

    assert request.window_start == old_end
    assert request.window_end == now


def test_build_window_request_applies_max_window_limit(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])
    monkeypatch.setattr(config, "MAX_PENDING_WINDOWS", 10)

    request = trigger.build_window_request(store=store, now=now)

    assert request.max_windows == 10


def test_build_window_request_can_override_job_id(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])
    request = trigger.build_window_request(store=store, now=now, job_id="custom-job")

    assert request.job_id == "custom-job"


def test_lambda_handler_uses_rest_api_table_by_default(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    stub_store = _populate_store(temporary_window_status_table, [])
    captured: dict[str, bool] = {}

    def fake_build_window_store(*, use_rest_api_table: bool) -> IcebergWindowStore:
        captured["flag"] = use_rest_api_table
        return stub_store

    def fake_handler(
        event: AxiellAdapterTriggerEvent,
        runtime: trigger.TriggerRuntime,
        *,
        enforce_lag: bool = True,
    ) -> AxiellAdapterLoaderEvent:
        assert runtime.store is stub_store
        assert enforce_lag is True
        now = event.now or datetime.now(tz=UTC)
        return AxiellAdapterLoaderEvent(
            job_id=event.job_id,
            window_key="window",
            window_start=now - timedelta(minutes=random.randint(2, 40)),
            window_end=now,
            metadata_prefix="oai",
            set_spec="collect",
        )

    monkeypatch.setattr(trigger, "build_window_store", fake_build_window_store)
    monkeypatch.setattr(trigger, "handler", fake_handler)

    trigger.lambda_handler({"time": "2025-11-17T12:00:00Z"}, context=None)

    assert captured["flag"] is True
