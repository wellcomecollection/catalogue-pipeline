"""Tests for the OAI-PMH adapter trigger step.

These tests verify the shared trigger implementation used by all OAI-PMH adapters.
Tests are parameterized to run with both Axiell and FOLIO configurations.
"""

from __future__ import annotations

import random
from datetime import UTC, datetime, timedelta

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.oai_pmh.steps import trigger
from adapters.oai_pmh.steps.trigger import TriggerRuntime, build_window_request
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_store import WindowStore
from clients.chatbot_notifier import ChatbotNotifier
from tests.adapters.oai_pmh.conftest import create_window_row, populate_window_store
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
        oai_metadata_prefix=cfg.oai_metadata_prefix,
        oai_set_spec=cfg.oai_set_spec,
        adapter_name=cfg.adapter_name,
    )


# ---------------------------------------------------------------------------
# build_window_request tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestBuildWindowRequest:
    """Tests for the build_window_request function."""

    def test_uses_lookback_when_no_history(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that lookback is used when no successful windows exist."""
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
        """Test that custom lookback days is respected."""
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
        """Test that window_minutes is embedded in the request."""
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
        """Test that last successful window end time is used as start."""
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
        """Test that the latest successful window is used when multiple exist."""
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
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == end2
        assert request.window.end_time == now

    def test_errors_when_lag_exceeds_limit(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that an error is raised when lag exceeds the limit."""
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
        """Test that lag enforcement can be disabled."""
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
        """Test that max_pending_windows is applied."""
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
        """Test that custom job_id is used when provided."""
        now = datetime(2025, 11, 17, 12, 45, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        request = build_window_request(runtime=runtime, now=now, job_id="custom-job")

        assert request.job_id == "custom-job"


# ---------------------------------------------------------------------------
# Window gap notification tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestWindowGapNotifications:
    """Tests for window gap detection and notification."""

    def test_notifies_when_gaps_detected(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that notifier is called when coverage gaps are detected."""
        MockSNSClient.reset_mocks()

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
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # Should have sent a notification (there's a 2-hour gap between windows)
        assert len(MockSNSClient.publish_calls) == 1

        # Should still create the window request
        assert request.job_id == "20251202T1200"
        assert request.window.start_time == second_end
        assert request.window.end_time == now

    def test_does_not_notify_without_gaps(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that notifier is not called when no coverage gaps exist."""
        MockSNSClient.reset_mocks()

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
        """Test that build_window_request works when notifier is None."""
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
        """Test that notifier is not called when lag enforcement raises an error."""
        MockSNSClient.reset_mocks()

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
        """Test that gaps between start_time and now are NOT reported.

        These gaps are about to be processed by the current batch.
        """
        MockSNSClient.reset_mocks()

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

    def test_notifies_for_historical_gaps_only(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that only historical gaps (before start_time) are reported."""
        MockSNSClient.reset_mocks()

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
        )

        runtime = _create_trigger_runtime(
            store,
            adapter_runtime_config,
            notifier=notifier,
            enforce_lag=False,
        )

        request = build_window_request(runtime=runtime, now=now, job_id="20251202T1200")

        # SHOULD have sent a notification for the historical gap
        assert len(MockSNSClient.publish_calls) == 1

        # The request should cover from last_success_end to now
        assert request.window.start_time == window2_end
        assert request.window.end_time == now


# ---------------------------------------------------------------------------
# handler tests
# ---------------------------------------------------------------------------
class TestHandler:
    """Tests for the handler function."""

    def test_handler_creates_loader_event(
        self,
        temporary_window_status_table: IcebergTable,
        adapter_runtime_config: OAIPMHRuntimeConfig,
    ) -> None:
        """Test that handler returns a properly formatted loader event."""
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = populate_window_store(temporary_window_status_table, [])
        runtime = _create_trigger_runtime(store, adapter_runtime_config)

        from adapters.oai_pmh.models.step_events import OAIPMHTriggerEvent

        event = OAIPMHTriggerEvent(now=now, job_id="test-job")
        result = trigger.handler(event, runtime)

        assert isinstance(result, OAIPMHLoaderEvent)
        assert result.job_id == "test-job"
        assert result.window.end_time == now
