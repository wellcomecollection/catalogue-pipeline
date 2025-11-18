from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from adapters.axiell import config
from adapters.axiell.steps import trigger


class StubWindowStore:
    def __init__(self, rows: list[dict]):
        self._rows = rows

    def list_by_state(self, state: str) -> list[dict]:
        if state != "success":
            return []
        return self._rows


def _window_row(start: datetime, end: datetime) -> dict:
    return {
        "window_key": f"{start.isoformat()}_{end.isoformat()}",
        "window_start": start,
        "window_end": end,
        "state": "success",
        "attempts": 1,
        "last_error": None,
        "record_ids": [],
        "updated_at": end,
    }


def test_build_window_request_uses_lookback_when_no_history(monkeypatch):
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    monkeypatch.setattr(config, "WINDOW_LOOKBACK_DAYS", 1)
    store = StubWindowStore([])

    request = trigger.build_window_request(store=store, now=now)

    assert request.window_start == now - timedelta(days=1)
    assert request.window_end == now
    assert request.set_spec == config.OAI_SET_SPEC
    assert request.metadata_prefix == config.OAI_METADATA_PREFIX


def test_build_window_request_respects_last_success(monkeypatch):
    now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
    last_success_end = now - timedelta(minutes=15)
    store = StubWindowStore(
        [_window_row(last_success_end - timedelta(minutes=15), last_success_end)]
    )

    request = trigger.build_window_request(store=store, now=now)

    assert request.window_start == last_success_end
    assert request.window_end == now


def test_build_window_request_errors_when_lag_exceeds_limit(monkeypatch):
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = StubWindowStore([_window_row(old_end - timedelta(minutes=15), old_end)])
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    with pytest.raises(RuntimeError):
        trigger.build_window_request(store=store, now=now)


def test_build_window_request_can_skip_lag_enforcement(monkeypatch):
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = StubWindowStore([_window_row(old_end - timedelta(minutes=15), old_end)])
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    request = trigger.build_window_request(store=store, now=now, enforce_lag=False)

    assert request.window_start == old_end
    assert request.window_end == now


def test_build_window_request_applies_max_window_limit(monkeypatch):
    now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
    store = StubWindowStore([])
    monkeypatch.setattr(config, "MAX_PENDING_WINDOWS", 10)

    request = trigger.build_window_request(store=store, now=now)

    assert request.max_windows == 10


def test_build_window_request_uses_rest_api_table_by_default(monkeypatch):
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    stub_store = StubWindowStore([])
    captured: dict[str, bool] = {}

    def fake_build_window_store(*, use_rest_api_table: bool):
        captured["flag"] = use_rest_api_table
        return stub_store

    monkeypatch.setattr(trigger, "build_window_store", fake_build_window_store)

    trigger.build_window_request(now=now)

    assert captured["flag"] is True


def test_build_window_request_can_target_local_window_store(monkeypatch):
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    stub_store = StubWindowStore([])
    captured: dict[str, bool] = {}

    def fake_build_window_store(*, use_rest_api_table: bool):
        captured["flag"] = use_rest_api_table
        return stub_store

    monkeypatch.setattr(trigger, "build_window_store", fake_build_window_store)

    trigger.build_window_request(now=now, use_rest_api_table=False)

    assert captured["flag"] is False
