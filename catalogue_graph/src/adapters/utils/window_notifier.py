"""Notifier for window coverage gaps via AWS Chatbot.

Sends formatted Slack/Teams notifications when harvesting windows have
coverage gaps, including gap details and trigger context.
"""

from __future__ import annotations

from datetime import datetime

from clients.chatbot_notifier import ChatbotMessage, ChatbotNotifier

from .window_reporter import WindowCoverageReport


class WindowNotifier:
    """Send notifications about window coverage gaps.

    Formats window coverage gaps into user-friendly Slack messages with:
    - Summary of missing windows with total hours
    - Limited list of gap details (first 5) to prevent message overflow
    - Trigger context (job_id, timestamp) for debugging
    - Thread grouping by 6-hour chunks to prevent spam

    Example:
        >>> from clients.chatbot_notifier import ChatbotNotifier
        >>> notifier = WindowNotifier(
        ...     chatbot_notifier=chatbot_notifier,
        ...     table_name="axiell_window_status.window_status",
        ... )
        >>> notifier.notify_if_gaps(
        ...     report=coverage_report,
        ...     job_id="20251202T1200",
        ...     trigger_time=datetime.now(UTC),
        ... )
    """

    MAX_GAPS_TO_DISPLAY = 5

    def __init__(
        self,
        chatbot_notifier: ChatbotNotifier | None,
        table_name: str,
    ) -> None:
        """Initialize the WindowNotifier.

        Args:
            chatbot_notifier: ChatbotNotifier instance for sending messages.
                If None, notifications are disabled.
            table_name: Fully qualified table name (e.g., "namespace.table").
        """
        self.chatbot_notifier = chatbot_notifier
        self.table_name = table_name

    def notify_if_gaps(
        self,
        report: WindowCoverageReport,
        job_id: str | None = None,
        trigger_time: datetime | None = None,
    ) -> None:
        """Send notification if coverage gaps are detected.

        Args:
            report: Window coverage report containing gap information.
            job_id: Optional job identifier for context.
            trigger_time: Optional trigger timestamp for context and threading.
        """
        if not self.chatbot_notifier:
            return

        if not report.coverage_gaps:
            return

        message = self._format_message(report, job_id, trigger_time)
        thread_id = self._generate_thread_id(trigger_time) if trigger_time else None

        self.chatbot_notifier.send_notification(
            ChatbotMessage(
                text=message,
                thread_id=thread_id,
                title="⚠️ Window Coverage Gaps Detected",
                next_steps=None,
                keywords=["axiell-adapter", "window-gaps", "harvesting"],
                summary=None,
                related_resources=None,
                additional_context=self._build_context(report, job_id, trigger_time),
                enable_custom_actions=False,
            )
        )

    def _format_message(
        self,
        report: WindowCoverageReport,
        job_id: str | None,
        trigger_time: datetime | None,
    ) -> str:
        """Format coverage gaps into a markdown message.

        Args:
            report: Window coverage report containing gap information.
            job_id: Optional job identifier.
            trigger_time: Optional trigger timestamp.

        Returns:
            Formatted markdown message.
        """
        total_gaps = len(report.coverage_gaps)
        total_missing_hours = sum(
            (gap.end - gap.start).total_seconds() / 3600.0
            for gap in report.coverage_gaps
        )

        lines = [
            f"Found **{total_gaps} coverage gap(s)** totaling "
            f"**{total_missing_hours:.1f} hours** of missing windows.",
            "",
            f"**Table:** `{self.table_name}`",
        ]

        if job_id:
            lines.append(f"**Job ID:** `{job_id}`")

        if trigger_time:
            lines.append(
                f"**Triggered:** {trigger_time.strftime('%Y-%m-%d %H:%M:%S UTC')}"
            )

        lines.extend(["", "## Coverage Gaps"])

        gaps_to_show = report.coverage_gaps[: self.MAX_GAPS_TO_DISPLAY]
        for i, gap in enumerate(gaps_to_show, 1):
            duration_hours = (gap.end - gap.start).total_seconds() / 3600.0
            lines.append(
                f"{i}. **{gap.start.strftime('%Y-%m-%d %H:%M')}** → "
                f"**{gap.end.strftime('%Y-%m-%d %H:%M')}** "
                f"({duration_hours:.1f}h)"
            )

        if total_gaps > self.MAX_GAPS_TO_DISPLAY:
            remaining = total_gaps - self.MAX_GAPS_TO_DISPLAY
            lines.append(f"\n*...and {remaining} more gap(s)*")

        return "\n".join(lines)

    def _build_context(
        self,
        report: WindowCoverageReport,
        job_id: str | None,
        trigger_time: datetime | None,
    ) -> dict[str, str]:
        """Build additional context dictionary for the notification.

        Args:
            report: Window coverage report.
            job_id: Optional job identifier.
            trigger_time: Optional trigger timestamp.

        Returns:
            Dictionary of context key-value pairs.
        """
        context = {
            "table": self.table_name,
            "total_gaps": str(len(report.coverage_gaps)),
            "total_windows": str(report.total_windows),
        }

        if job_id:
            context["job_id"] = job_id

        if trigger_time:
            context["trigger_time"] = trigger_time.isoformat()

        if report.last_success_end:
            context["last_success_end"] = report.last_success_end.isoformat()

        return context

    @staticmethod
    def _generate_thread_id(trigger_time: datetime) -> str:
        """Generate thread ID based on 6-hour chunks to group notifications.

        Divides each day into 4 chunks:
        - 00:00-06:00
        - 06:00-12:00
        - 12:00-18:00
        - 18:00-24:00

        Args:
            trigger_time: Trigger timestamp.

        Returns:
            Thread ID string (e.g., "window-gaps-20251202-chunk0").
        """
        # Determine which 6-hour chunk (0-3)
        chunk = trigger_time.hour // 6

        # Format: window-gaps-YYYYMMDD-chunkN
        date_str = trigger_time.strftime("%Y%m%d")
        return f"window-gaps-{date_str}-chunk{chunk}"
