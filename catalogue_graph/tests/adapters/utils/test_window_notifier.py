"""Tests for the WindowNotifier class."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_reporter import CoverageGap, WindowCoverageReport
from clients.chatbot_notifier import ChatbotNotifier
from tests.mocks import MockSNSClient


@pytest.fixture(autouse=True)
def reset_mocks() -> None:
    """Reset mock state before each test."""
    MockSNSClient.reset_mocks()


@pytest.fixture
def mock_chatbot_notifier() -> ChatbotNotifier:
    """Provide a ChatbotNotifier with mock SNS client."""
    return ChatbotNotifier(
        sns_client=MockSNSClient(),
        topic_arn="arn:aws:sns:eu-west-1:123456789012:test-chatbot-topic",
    )


@pytest.fixture
def notifier(mock_chatbot_notifier: ChatbotNotifier) -> WindowNotifier:
    """Provide a WindowNotifier instance."""
    return WindowNotifier(
        chatbot_notifier=mock_chatbot_notifier,
        table_name="axiell_window_status.window_status",
    )


def test_does_not_notify_when_no_gaps(notifier: WindowNotifier) -> None:
    """Test that no notification is sent when there are no coverage gaps."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=10,
        coverage_gaps=[],
        last_success_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    assert len(MockSNSClient.publish_calls) == 0


def test_notifies_when_gaps_exist(notifier: WindowNotifier) -> None:
    """Test that a notification is sent when coverage gaps are detected."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
        last_success_end=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    assert len(MockSNSClient.publish_calls) == 1


def test_notification_includes_gap_summary(notifier: WindowNotifier) -> None:
    """Test that notification includes summary of total gaps and hours."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            ),
            CoverageGap(
                start=datetime(2025, 12, 1, 14, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 15, 30, tzinfo=UTC),
            ),
        ],
        last_success_end=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    text = payload["content"]["description"]

    assert "2 coverage gap(s)" in text
    assert "3.5 hours" in text  # 2 + 1.5 hours


def test_notification_limits_displayed_gaps(notifier: WindowNotifier) -> None:
    """Test that notification shows max 5 gaps with summary of remaining."""
    gaps = [
        CoverageGap(
            start=datetime(2025, 12, 1, i, 0, tzinfo=UTC),
            end=datetime(2025, 12, 1, i, 30, tzinfo=UTC),
        )
        for i in range(8)  # Create 8 gaps
    ]

    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=gaps,
        last_success_end=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    text = payload["content"]["description"]

    assert "8 coverage gap(s)" in text
    assert "4.0 hours" in text  # 8 * 0.5 hours
    assert "...and 3 more gap(s)" in text  # 8 - 5 = 3 remaining
    # Should show first 5 gaps
    assert "1." in text
    assert "5." in text


def test_notification_includes_table_name(notifier: WindowNotifier) -> None:
    """Test that notification includes the table name."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    text = payload["content"]["description"]

    assert "axiell_window_status.window_status" in text


def test_notification_includes_job_id_and_trigger_time(
    notifier: WindowNotifier,
) -> None:
    """Test that notification includes job ID and trigger timestamp."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    trigger_time = datetime(2025, 12, 2, 12, 30, 45, tzinfo=UTC)
    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1230",
        trigger_time=trigger_time,
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    text = payload["content"]["description"]
    metadata = payload["metadata"]

    assert "20251202T1230" in text
    assert "2025-12-02 12:30:45 UTC" in text
    assert metadata["additionalContext"]["job_id"] == "20251202T1230"


def test_notification_includes_additional_context(notifier: WindowNotifier) -> None:
    """Test that notification includes additional context in metadata."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=10,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
        last_success_end=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    context = payload["metadata"]["additionalContext"]

    assert context["table"] == "axiell_window_status.window_status"
    assert context["total_gaps"] == "1"
    assert context["total_windows"] == "10"
    assert context["job_id"] == "20251202T1200"
    assert "last_success_end" in context


def test_thread_id_groups_by_six_hour_chunks(notifier: WindowNotifier) -> None:
    """Test that thread ID is based on 6-hour chunks to prevent spam."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    # Test different times in the same chunk (06:00-12:00)
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 7, 0, tzinfo=UTC),
    )
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 11, 59, tzinfo=UTC),
    )

    import json

    # Both should have same thread ID
    thread1 = json.loads(
        json.loads(MockSNSClient.publish_calls[0]["Message"])["default"]
    )["metadata"]["threadId"]
    thread2 = json.loads(
        json.loads(MockSNSClient.publish_calls[1]["Message"])["default"]
    )["metadata"]["threadId"]

    assert thread1 == thread2
    assert thread1 == "window-gaps-20251202-chunk1"


def test_thread_id_differs_between_chunks(notifier: WindowNotifier) -> None:
    """Test that thread ID differs between different 6-hour chunks."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    # Chunk 0: 00:00-06:00
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 3, 0, tzinfo=UTC),
    )
    # Chunk 1: 06:00-12:00
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 9, 0, tzinfo=UTC),
    )
    # Chunk 2: 12:00-18:00
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 15, 0, tzinfo=UTC),
    )
    # Chunk 3: 18:00-24:00
    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 21, 0, tzinfo=UTC),
    )

    import json

    thread_ids = [
        json.loads(json.loads(call["Message"])["default"])["metadata"]["threadId"]
        for call in MockSNSClient.publish_calls
    ]

    assert thread_ids == [
        "window-gaps-20251202-chunk0",
        "window-gaps-20251202-chunk1",
        "window-gaps-20251202-chunk2",
        "window-gaps-20251202-chunk3",
    ]


def test_notification_includes_keywords(notifier: WindowNotifier) -> None:
    """Test that notification includes appropriate keywords."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    notifier.notify_if_gaps(
        report=report,
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    keywords = payload["content"]["keywords"]

    assert "axiell-adapter" in keywords
    assert "window-gaps" in keywords
    assert "harvesting" in keywords


def test_does_not_notify_when_notifier_is_none() -> None:
    """Test that no notification is sent when chatbot_notifier is None."""
    notifier = WindowNotifier(
        chatbot_notifier=None,
        table_name="axiell_window_status.window_status",
    )

    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    # Should not raise an error, just silently skip
    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    assert len(MockSNSClient.publish_calls) == 0


def test_notification_works_without_optional_context(notifier: WindowNotifier) -> None:
    """Test that notification works when job_id and trigger_time are not provided."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 0, tzinfo=UTC),
            )
        ],
    )

    notifier.notify_if_gaps(report=report)

    assert len(MockSNSClient.publish_calls) == 1

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])

    # Should not have thread_id when trigger_time is None
    assert "threadId" not in payload.get("metadata", {})


def test_single_gap_formatting(notifier: WindowNotifier) -> None:
    """Test that a single gap is formatted correctly."""
    report = WindowCoverageReport(
        range_start=datetime(2025, 12, 1, 0, 0, tzinfo=UTC),
        range_end=datetime(2025, 12, 2, 0, 0, tzinfo=UTC),
        total_windows=5,
        coverage_gaps=[
            CoverageGap(
                start=datetime(2025, 12, 1, 10, 0, tzinfo=UTC),
                end=datetime(2025, 12, 1, 12, 30, tzinfo=UTC),
            )
        ],
    )

    notifier.notify_if_gaps(
        report=report,
        job_id="20251202T1200",
        trigger_time=datetime(2025, 12, 2, 12, 0, tzinfo=UTC),
    )

    import json

    call = MockSNSClient.publish_calls[0]
    message_json = json.loads(call["Message"])
    payload = json.loads(message_json["default"])
    text = payload["content"]["description"]

    assert "1 coverage gap(s)" in text
    assert "2.5 hours" in text
    assert "2025-12-01 10:00" in text
    assert "2025-12-01 12:30" in text
    assert "(2.5h)" in text
    assert "...and" not in text  # No remaining gaps message
