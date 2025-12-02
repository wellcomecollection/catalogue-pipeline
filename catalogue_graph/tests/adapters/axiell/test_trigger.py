from __future__ import annotations

import random
from datetime import UTC, datetime, timedelta

import pytest
from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

from adapters.axiell import config, helpers
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    AxiellAdapterTriggerEvent,
)
from adapters.axiell.steps import trigger
from adapters.utils.window_store import WindowStatusRecord, WindowStore


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

    assert request.window_start == now - timedelta(days=1)
    assert request.window_end == now
    assert request.set_spec == config.OAI_SET_SPEC
    assert request.metadata_prefix == config.OAI_METADATA_PREFIX
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

    assert request.window_start == now - timedelta(days=3)
    assert request.window_end == now


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

    def fake_build_window_store(*, use_rest_api_table: bool) -> WindowStore:
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
            window_start=now - timedelta(minutes=random.randint(2, 40)),
            window_end=now,
            metadata_prefix="oai",
            set_spec="collect",
        )

    monkeypatch.setattr(helpers, "build_window_store", fake_build_window_store)
    monkeypatch.setattr(trigger, "handler", fake_handler)

    trigger.lambda_handler({"time": "2025-11-17T12:00:00Z"}, context=None)

    assert captured["flag"] is True


def test_build_window_request_notifies_when_gaps_detected(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that notifier is called when coverage gaps are detected."""
    from adapters.utils.window_notifier import WindowNotifier
    from clients.chatbot_notifier import ChatbotNotifier
    from tests.mocks import MockSNSClient

    MockSNSClient.reset_mocks()

    now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

    # Create a gap: two successful windows with a 2-hour gap between them
    first_end = now - timedelta(hours=4)
    second_start = now - timedelta(hours=2)
    second_end = now - timedelta(minutes=5)

    store = _populate_store(
        temporary_window_status_table,
        [
            _window_row(first_end - timedelta(minutes=15), first_end),
            _window_row(second_start, second_end),
        ],
    )

    chatbot_notifier = ChatbotNotifier(
        sns_client=MockSNSClient(),
        topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
    )
    notifier = WindowNotifier(
        chatbot_notifier=chatbot_notifier,
        table_name="test_table.window_status",
    )

    request = trigger.build_window_request(
        store=store,
        now=now,
        enforce_lag=False,
        job_id="20251202T1200",
        notifier=notifier,
    )

    # Should have sent a notification (there's a 2-hour gap between windows)
    assert len(MockSNSClient.publish_calls) == 1

    # Should still create the window request
    assert request.job_id == "20251202T1200"
    assert request.window_start == second_end
    assert request.window_end == now


def test_build_window_request_does_not_notify_without_gaps(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that notifier is not called when no coverage gaps exist."""
    from adapters.utils.window_notifier import WindowNotifier
    from clients.chatbot_notifier import ChatbotNotifier
    from tests.mocks import MockSNSClient

    MockSNSClient.reset_mocks()

    now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

    # Create recent successful window (5 minutes ago)
    recent_end = now - timedelta(minutes=5)
    store = _populate_store(
        temporary_window_status_table,
        [_window_row(recent_end - timedelta(minutes=15), recent_end)],
    )

    chatbot_notifier = ChatbotNotifier(
        sns_client=MockSNSClient(),
        topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
    )
    notifier = WindowNotifier(
        chatbot_notifier=chatbot_notifier,
        table_name="test_table.window_status",
    )

    request = trigger.build_window_request(
        store=store,
        now=now,
        job_id="20251202T1200",
        notifier=notifier,
    )

    # Should NOT have sent a notification (no gaps)
    assert len(MockSNSClient.publish_calls) == 0

    # Should still create the window request
    assert request.job_id == "20251202T1200"
    assert request.window_start == recent_end
    assert request.window_end == now


def test_build_window_request_works_without_notifier(
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that build_window_request works when notifier is None."""
    now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)
    store = _populate_store(temporary_window_status_table, [])

    # Should not raise an error
    request = trigger.build_window_request(
        store=store,
        now=now,
        job_id="20251202T1200",
        notifier=None,
    )

    assert request.job_id == "20251202T1200"


def test_build_window_request_does_not_notify_when_lag_breaker_trips(
    monkeypatch: MonkeyPatch,
    temporary_window_status_table: IcebergTable,
) -> None:
    """Test that notifier is not called when lag enforcement raises an error."""
    from adapters.utils.window_notifier import WindowNotifier
    from clients.chatbot_notifier import ChatbotNotifier
    from tests.mocks import MockSNSClient

    MockSNSClient.reset_mocks()

    now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

    # Create old successful window that will trigger lag error
    old_end = now - timedelta(hours=8)
    store = _populate_store(
        temporary_window_status_table,
        [_window_row(old_end - timedelta(minutes=15), old_end)],
    )

    chatbot_notifier = ChatbotNotifier(
        sns_client=MockSNSClient(),
        topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
    )
    notifier = WindowNotifier(
        chatbot_notifier=chatbot_notifier,
        table_name="test_table.window_status",
    )

    monkeypatch.setattr(config, "MAX_LAG_MINUTES", 60)

    # Should raise RuntimeError due to lag
    with pytest.raises(RuntimeError, match="too far behind"):
        trigger.build_window_request(
            store=store,
            now=now,
            enforce_lag=True,
            job_id="20251202T1200",
            notifier=notifier,
        )

    # Should NOT have sent a notification (error raised before notification)
    assert len(MockSNSClient.publish_calls) == 0
