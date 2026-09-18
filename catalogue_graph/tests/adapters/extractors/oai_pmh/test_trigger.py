"""Tests for the OAI-PMH adapter trigger step.

These tests verify the shared trigger implementation used by all OAI-PMH adapters.
Tests are parameterized to run with both Axiell and FOLIO configurations.
"""

from __future__ import annotations

import random
from datetime import UTC, datetime, timedelta

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.extractors.oai_pmh.models.step_events import OAIPMHLoaderEvent
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.steps.oai_pmh import trigger
from adapters.steps.oai_pmh.trigger import (
    TriggerRuntime,
    build_window_request,
)
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_store import WindowStore
from clients.chatbot_notifier import ChatbotNotifier
from models.incremental_window import IncrementalWindow
from tests.adapters.extractors.oai_pmh.conftest import (
    create_window_row,
    populate_window_store,
)
from tests.mocks import MockSNSClient


def _create_trigger_runtime(
    store: WindowStore,
    adapter_runtime_config: OAIPMHRuntimeConfig,
    *,
    notifier: WindowNotifier | None = None,
    enforce_lag: bool = True,
    window_minutes: int | None = None,
    window_lookback_days: int | None = None,
    max_lag_minutes: int | None = None,
    max_pending_windows: int | None = None,
    auto_retry_failed_windows: bool = True,
) -> TriggerRuntime:
    """Create a TriggerRuntime for testing."""
    cfg = adapter_runtime_config.config
    return TriggerRuntime(
        store=store,
        notifier=notifier,
        enforce_lag=enforce_lag,
        window_minutes=window_minutes or cfg.window_minutes,
        window_lookback_days=window_lookback_days or cfg.window_lookback_days,
        max_lag_minutes=max_lag_minutes or cfg.max_lag_minutes,
        max_pending_windows=max_pending_windows or cfg.max_pending_windows,
        auto_retry_failed_windows=auto_retry_failed_windows,
        oai_metadata_prefix=cfg.oai_metadata_prefix,
        oai_set_spec=cfg.oai_set_spec,
        adapter_name=cfg.adapter_name,
    )


# ---------------------------------------------------------------------------
# build_window_request tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestBuildWindowRequest:
    def test_uses_lookback_when_no_history(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            window_lookback_days=1,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == now - timedelta(days=1)
        assert request.window.end_time == now
        assert request.set_spec == adapter_runtime_config.config.oai_set_spec
        assert (
            request.metadata_prefix == adapter_runtime_config.config.oai_metadata_prefix
        )
        assert request.job_id == "20251117T1200"

    def test_respects_custom_lookback(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            window_lookback_days=3,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == now - timedelta(days=3)
        assert request.window.end_time == now

    def test_embeds_window_minutes(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            window_minutes=42,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window_minutes == 42

    def test_respects_last_success(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
        minutes_ago = random.randint(2, 40)
        last_success_end = now - timedelta(minutes=minutes_ago)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    last_success_end - timedelta(minutes=minutes_ago), last_success_end
                )
            ],
        )
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == last_success_end
        assert request.window.end_time == now

    def test_finds_latest_among_multiple_windows(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)

        # Create windows with different end times
        end1 = now - timedelta(minutes=45)
        end2 = now - timedelta(minutes=30)  # This is the latest
        end3 = now - timedelta(minutes=60)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    end1 - timedelta(minutes=random.randint(2, 40)), end1
                ),
                create_window_row(
                    end2 - timedelta(minutes=random.randint(2, 40)), end2
                ),
                create_window_row(
                    end3 - timedelta(minutes=random.randint(2, 40)), end3
                ),
            ],
        )
        # Random widths can leave gaps between the rows; only the cursor matters here
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, auto_retry_failed_windows=False
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == end2
        assert request.window.end_time == now

    def test_resumes_from_last_published_window(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Loaded-but-unpublished windows must not advance the start: the next
        range re-covers them so their changesets are re-emitted and re-published."""
        now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
        published_end = now - timedelta(minutes=45)
        stranded_end = now - timedelta(minutes=30)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    published_end - timedelta(minutes=15),
                    published_end,
                    tags={"published_at": "2025-11-17T11:30:00+00:00"},
                ),
                # Loaded successfully but the execution died before publishing
                create_window_row(published_end, stranded_end),
            ],
        )
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == published_end
        assert request.window.end_time == now

    def test_cascades_to_last_success_when_nothing_published(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 15, tzinfo=UTC)
        last_success_end = now - timedelta(minutes=30)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    last_success_end - timedelta(minutes=15), last_success_end
                )
            ],
        )
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == last_success_end

    def test_stale_published_cursor_recovers_without_tripping_breaker(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Lag measures harvest liveness, not the published cursor: a stamp
        left stale by a stalled mark-published step must not halt the
        trigger, because only a completed execution can refresh the stamps.
        The run proceeds and re-covers the stale range instead."""
        now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
        published_end = now - timedelta(hours=2)
        fresh_success_end = now - timedelta(minutes=15)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    published_end - timedelta(minutes=15),
                    published_end,
                    tags={"published_at": "2025-11-17T11:00:00+00:00"},
                ),
                create_window_row(
                    fresh_success_end - timedelta(minutes=15), fresh_success_end
                ),
            ],
        )
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            max_lag_minutes=30,
            enforce_lag=True,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == published_end
        assert request.window.end_time == now

    def test_errors_when_lag_exceeds_limit(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
        old_end = now - timedelta(hours=2)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    old_end - timedelta(minutes=random.randint(2, 40)), old_end
                )
            ],
        )
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            max_lag_minutes=30,
            enforce_lag=True,
        )

        with pytest.raises(RuntimeError):
            build_window_request(runtime=runtime, now=now)

    def test_can_skip_lag_enforcement(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 13, 0, tzinfo=UTC)
        old_end = now - timedelta(hours=2)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    old_end - timedelta(minutes=random.randint(2, 40)), old_end
                )
            ],
        )
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            max_lag_minutes=30,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == old_end
        assert request.window.end_time == now

    def test_applies_max_window_limit(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            max_pending_windows=10,
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.max_windows == 10

    def test_can_override_job_id(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now, job_id="custom-job")

        assert request.job_id == "custom-job"


# ---------------------------------------------------------------------------
# Window gap notification tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestWindowGapNotifications:
    def test_retries_recent_gap_without_notifying(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

        # Create a gap: two successful windows with a 2-hour gap between them
        first_end = now - timedelta(hours=4)
        second_start = now - timedelta(hours=2)
        second_end = now - timedelta(minutes=5)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(first_end - timedelta(minutes=15), first_end),
                create_window_row(second_start, second_end),
            ],
        )

        chatbot_notifier = ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        )
        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name="test_table.window_status",
            adapter_name="test-adapter",
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # The 2-hour gap became stranded 5 minutes ago and this run retries it,
        # so it is still inside its grace period.
        assert len(MockSNSClient.publish_calls) == 0

        assert request.job_id == "20251202T1200"
        assert request.window.start_time == first_end
        assert request.window.end_time == now

    def test_does_not_notify_without_gaps(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

        # Create recent successful window (5 minutes ago)
        recent_end = now - timedelta(minutes=5)
        store = populate_window_store(
            temporary_window_status_table,
            [create_window_row(recent_end - timedelta(minutes=15), recent_end)],
        )

        chatbot_notifier = ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        )
        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name="test_table.window_status",
            adapter_name="test-adapter",
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # Should NOT have sent a notification (no gaps)
        assert len(MockSNSClient.publish_calls) == 0

        # Should still create the window request
        assert request.job_id == "20251202T1200"
        assert request.window.start_time == recent_end
        assert request.window.end_time == now

    def test_works_without_notifier(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=None,
        )

        # Should not raise an error
        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        assert request.job_id == "20251202T1200"

    def test_does_not_notify_when_lag_breaker_trips(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

        # Create old successful window that will trigger lag error
        old_end = now - timedelta(hours=8)
        store = populate_window_store(
            temporary_window_status_table,
            [create_window_row(old_end - timedelta(minutes=15), old_end)],
        )

        chatbot_notifier = ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        )
        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name="test_table.window_status",
            adapter_name="test-adapter",
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            max_lag_minutes=60,
            enforce_lag=True,
        )

        # Should raise RuntimeError due to lag
        with pytest.raises(RuntimeError, match="too far behind"):
            build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # Should NOT have sent a notification (error raised before notification)
        assert len(MockSNSClient.publish_calls) == 0

    def test_does_not_notify_for_gaps_about_to_be_processed(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

        # Single successful window that ended 3 hours ago.
        # The gap from last_success_end to now should NOT trigger notification.
        last_success_end = now - timedelta(hours=3)
        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    last_success_end - timedelta(minutes=15), last_success_end
                )
            ],
        )

        chatbot_notifier = ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        )
        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name="test_table.window_status",
            adapter_name="test-adapter",
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # Should NOT have sent a notification
        assert len(MockSNSClient.publish_calls) == 0

        # Should create the window request covering the gap
        assert request.window.start_time == last_success_end
        assert request.window.end_time == now

    def test_retries_historical_gaps_within_grace(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 0, tzinfo=UTC)

        # Create windows with a historical gap:
        # Window 1: 6 hours ago (ends at -6h)
        # Gap: 2 hours (from -6h to -4h) - HISTORICAL, should be reported
        # Window 2: 4 hours ago to 30 min ago (latest successful window)
        # Gap: 30 minutes (from -30min to now) - about to be processed, NOT reported

        window1_end = now - timedelta(hours=6)
        window2_start = now - timedelta(hours=4)
        window2_end = now - timedelta(minutes=30)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(window1_end - timedelta(minutes=15), window1_end),
                create_window_row(window2_start, window2_end),
            ],
        )

        chatbot_notifier = ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        )
        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name="test_table.window_status",
            adapter_name="test-adapter",
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # The historical gap became stranded 30 minutes ago: retried, not yet reported
        assert len(MockSNSClient.publish_calls) == 0

        # The request reaches back to the historical gap
        assert request.window.start_time == window1_end
        assert request.window.end_time == now


def _notifier() -> WindowNotifier:
    return WindowNotifier(
        chatbot_notifier=ChatbotNotifier(
            sns_client=MockSNSClient(),
            topic_arn="arn:aws:sns:eu-west-1:123456789012:test-topic",
        ),
        table_name="test_table.window_status",
        adapter_name="test-adapter",
    )


class TestStrandedGapRetry:
    """A failed window behind the cursor is retried, then reported if it persists."""

    @staticmethod
    def _rows_with_stranded_gap(
        now: datetime, stranded_for: timedelta, gap_age: timedelta = timedelta(hours=20)
    ) -> tuple[list, datetime]:
        gap_start = now - gap_age
        gap_end = gap_start + timedelta(minutes=30)
        stranded_at = now - stranded_for
        rows = [
            create_window_row(gap_start - timedelta(minutes=15), gap_start),
            create_window_row(gap_start, gap_start + timedelta(minutes=15), "failed"),
            create_window_row(gap_start + timedelta(minutes=15), gap_end, "failed"),
            create_window_row(
                gap_end,
                now - timedelta(minutes=15),
                tags={"published_at": stranded_at.isoformat()},
            ),
        ]
        return rows, gap_start

    def test_reaches_back_to_stranded_gap(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        rows, gap_start = self._rows_with_stranded_gap(now, timedelta(minutes=10))
        store = populate_window_store(temporary_window_status_table, rows)
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == gap_start
        assert request.window.end_time == now

    def test_can_be_switched_off(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        rows, _ = self._rows_with_stranded_gap(now, timedelta(minutes=10))
        store = populate_window_store(temporary_window_status_table, rows)
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, auto_retry_failed_windows=False
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == now - timedelta(minutes=15)

    def test_leaves_gaps_older_than_lookback(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        rows, _ = self._rows_with_stranded_gap(
            now, timedelta(minutes=10), gap_age=timedelta(days=3)
        )
        store = populate_window_store(temporary_window_status_table, rows)
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, window_lookback_days=2, enforce_lag=False
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == now - timedelta(minutes=15)

    def test_leaves_backlog_larger_than_lag_tolerance(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        rows, _ = self._rows_with_stranded_gap(now, timedelta(minutes=10))
        store = populate_window_store(temporary_window_status_table, rows)
        # The gap is 30 minutes long
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, max_lag_minutes=20, enforce_lag=False
        )

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == now - timedelta(minutes=15)

    @pytest.mark.parametrize(
        "stranded_for, now, expected_notifications",
        [
            # Inside the grace period: retries get a chance first
            (timedelta(minutes=40), datetime(2025, 12, 2, 12, 13, tzinfo=UTC), 0),
            # First run after three retries failed
            (timedelta(minutes=50), datetime(2025, 12, 2, 12, 13, tzinfo=UTC), 1),
            # Already reported: quiet until the digest run
            (timedelta(hours=3), datetime(2025, 12, 2, 12, 13, tzinfo=UTC), 0),
            (timedelta(hours=3), datetime(2025, 12, 2, 8, 13, tzinfo=UTC), 1),
            (timedelta(hours=3), datetime(2025, 12, 2, 8, 28, tzinfo=UTC), 0),
        ],
    )
    def test_reports_once_then_daily(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        stranded_for: timedelta,
        now: datetime,
        expected_notifications: int,
    ) -> None:
        rows, _ = self._rows_with_stranded_gap(now, stranded_for)
        store = populate_window_store(temporary_window_status_table, rows)
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, notifier=_notifier()
        )

        build_window_request(runtime=runtime, now=now)

        assert len(MockSNSClient.publish_calls) == expected_notifications

    def test_reports_unretried_gap_straight_away(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        rows, _ = self._rows_with_stranded_gap(now, timedelta(minutes=10))
        store = populate_window_store(temporary_window_status_table, rows)
        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=_notifier(),
            auto_retry_failed_windows=False,
        )

        build_window_request(runtime=runtime, now=now)

        assert len(MockSNSClient.publish_calls) == 1


class TestOperatorWindow:
    def test_uses_supplied_window_as_given(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 12, 2, 12, 13, tzinfo=UTC)
        # A cursor far enough behind to trip the lag breaker on a normal run
        stale_end = now - timedelta(days=2)
        store = populate_window_store(
            temporary_window_status_table,
            [create_window_row(stale_end - timedelta(minutes=15), stale_end)],
        )
        runtime = _create_trigger_runtime(
            store, adapter_runtime_config, notifier=_notifier()
        )
        window = IncrementalWindow(
            start_time=datetime(2025, 11, 1, 9, 0, tzinfo=UTC),
            end_time=datetime(2025, 11, 1, 10, 0, tzinfo=UTC),
        )

        request = build_window_request(runtime=runtime, now=now, window=window)

        assert request.window == window
        assert request.job_id == "backfill-20251202T1213"
        assert len(MockSNSClient.publish_calls) == 0

    def test_lambda_handler_reads_window_from_event(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(store, adapter_runtime_config)
        monkeypatch.setattr(trigger, "get_config", lambda _: adapter_runtime_config)
        monkeypatch.setattr(trigger, "build_runtime", lambda _: runtime)

        response = trigger.lambda_handler(
            {
                "adapter_type": adapter_runtime_config.config.adapter_name,
                "time": "2025-12-02T12:13:00Z",
                "window": {
                    "start_time": "2025-11-01T09:00:00Z",
                    "end_time": "2025-11-01T10:00:00Z",
                },
            },
            None,
        )

        assert response["job_id"] == "backfill-20251202T1213"
        loader_event = OAIPMHLoaderEvent.model_validate(response)
        assert loader_event.window.start_time == datetime(2025, 11, 1, 9, tzinfo=UTC)
        assert loader_event.window.end_time == datetime(2025, 11, 1, 10, tzinfo=UTC)


# ---------------------------------------------------------------------------
# handler tests
# ---------------------------------------------------------------------------
class TestHandler:
    def test_handler_creates_loader_event(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        from adapters.extractors.oai_pmh.models.step_events import OAIPMHTriggerEvent

        event = OAIPMHTriggerEvent(now=now, job_id="test-job", adapter_type="axiell")
        result = trigger.handler(event, runtime)

        assert isinstance(result, OAIPMHLoaderEvent)
        assert result.job_id == "test-job"
        assert result.window.end_time == now
