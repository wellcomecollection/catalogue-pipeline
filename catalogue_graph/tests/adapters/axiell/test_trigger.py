from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import cast

import pytest
from _pytest.monkeypatch import MonkeyPatch

from adapters.axiell import config
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    AxiellAdapterTriggerEvent,
)
from adapters.axiell.steps import trigger
from adapters.utils.window_store import IcebergWindowStore


class StubWindowStore:
    def __init__(self, rows: list[dict]):
        self._rows = rows

    def list_by_state(self, state: str) -> list[dict]:
        if state != "success":
            return []
        return self._rows


def _window_row(start: datetime, end: datetime) -> dict[str, object]:
    return {
        "window_key": f"{start.isoformat()}_{end.isoformat()}",
        "window_start": start,
        "window_end": end,
        "state": "success",
        "attempts": 1,
        "last_error": None,
        "record_ids": [],
        "updated_at": end,
        "tags": None,
    }


def _stub_store(rows: list[dict]) -> IcebergWindowStore:
    return cast(IcebergWindowStore, StubWindowStore(rows))


def test_build_window_request_uses_lookback_when_no_history(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
    monkeypatch.setattr(config, "WINDOW_LOOKBACK_DAYS", 1)
    store = _stub_store([])

    request = trigger.build_window_request(store=store, now=now)

    assert request.window_start == now - timedelta(days=1)
    assert request.window_end == now
    assert request.set_spec == config.OAI_SET_SPEC
    assert request.metadata_prefix == config.OAI_METADATA_PREFIX
    assert request.job_id


def test_build_window_request_respects_last_success(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
    last_success_end = now - timedelta(minutes=15)
    store = _stub_store(
        [_window_row(last_success_end - timedelta(minutes=15), last_success_end)]
    )

    request = trigger.build_window_request(store=store, now=now)

    assert request.window_start == last_success_end
    assert request.window_end == now


def test_build_window_request_errors_when_lag_exceeds_limit(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = _stub_store([_window_row(old_end - timedelta(minutes=15), old_end)])
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    with pytest.raises(RuntimeError):
        trigger.build_window_request(store=store, now=now)


def test_build_window_request_can_skip_lag_enforcement(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
    old_end = now - timedelta(hours=2)
    store = _stub_store([_window_row(old_end - timedelta(minutes=15), old_end)])
    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 30)

    request = trigger.build_window_request(store=store, now=now, enforce_lag=False)

    assert request.window_start == old_end
    assert request.window_end == now


def test_build_window_request_applies_max_window_limit(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
    store = _stub_store([])
    monkeypatch.setattr(config, "MAX_PENDING_WINDOWS", 10)

    request = trigger.build_window_request(store=store, now=now)

    assert request.max_windows == 10


def test_build_window_request_can_override_job_id(
    monkeypatch: MonkeyPatch,
) -> None:
    now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
    store = _stub_store([])
    request = trigger.build_window_request(store=store, now=now, job_id="custom-job")

    assert request.job_id == "custom-job"


def test_lambda_handler_uses_rest_api_table_by_default(
    monkeypatch: MonkeyPatch,
) -> None:
    stub_store = _stub_store([])
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
            window_start=now - timedelta(minutes=15),
            window_end=now,
            metadata_prefix="oai",
            set_spec="collect",
        )

    monkeypatch.setattr(trigger, "build_window_store", fake_build_window_store)
    monkeypatch.setattr(trigger, "handler", fake_handler)

    trigger.lambda_handler({"time": "2025-11-17T12:00:00Z"}, context=None)

    assert captured["flag"] is True
