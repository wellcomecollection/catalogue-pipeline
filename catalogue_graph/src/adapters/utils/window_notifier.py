"""Notifier for window coverage gaps via AWS Chatbot.

Sends formatted Slack/Teams notifications when harvesting windows have
coverage gaps, including gap details and trigger context.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta

from clients.chatbot_notifier import ChatbotMessage, ChatbotNotifier

from .window_reporter import CoverageGap, WindowCoverageReport


class WindowNotifier:
    """Send notifications about window coverage gaps.

    Formats window coverage gaps into user-friendly Slack messages with:
    - Summary of missing windows with total hours
    - Limited list of gap details (first 5) to prevent message overflow
    - Trigger context (job_id, timestamp) for debugging
    - Thread grouping by 6-hour chunks to prevent spam

    A gap the trigger is retrying is not reported until the retries have had
    RETRY_GRACE_RUNS scheduled runs to close it. A gap is reported on the run
    where it first becomes reportable, and after that once a day in the
    DIGEST_HOUR_UTC run.

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
    RETRY_GRACE_RUNS = 3
    DIGEST_HOUR_UTC = 8

    def __init__(
        self,
        chatbot_notifier: ChatbotNotifier,
        table_name: str,
        adapter_name: str,
        run_interval_minutes: int = 15,
    ) -> None:
        """Initialize the WindowNotifier.

        Args:
            chatbot_notifier: ChatbotNotifier instance for sending messages.
            table_name: Fully qualified table name (e.g., "namespace.table").
            adapter_name: Adapter type used in remediation commands.
            run_interval_minutes: Minutes between scheduled runs.
        """
        self.chatbot_notifier = chatbot_notifier
        self.table_name = table_name
        self.adapter_name = adapter_name
        self.run_interval_minutes = run_interval_minutes

    def notify_if_gaps(
        self,
        report: WindowCoverageReport,
        job_id: str | None = None,
        trigger_time: datetime | None = None,
        retry_from: datetime | None = None,
    ) -> None:
        """Send notification if coverage gaps are due a report.

        Args:
            report: Window coverage report containing gap information.
            job_id: Optional job identifier for context.
            trigger_time: Optional trigger timestamp for context and threading.
            retry_from: Start of the range this run retries, if it retries any gaps.
        """
        gaps = self._gaps_to_report(report.coverage_gaps, trigger_time, retry_from)
        if not gaps:
            return
        report = report.model_copy(update={"coverage_gaps": gaps})

        message = self._format_message(report, job_id, trigger_time)
        thread_id = self._generate_thread_id(trigger_time) if trigger_time else None
        next_steps = self._build_next_steps(report, job_id)

        self.chatbot_notifier.send_notification(
            ChatbotMessage(
                text=message,
                thread_id=thread_id,
                title="⚠️ Window Coverage Gaps Detected",
                next_steps=next_steps,
                keywords=["axiell-adapter", "window-gaps", "harvesting"],
                summary=None,
                related_resources=None,
                additional_context=self._build_context(report, job_id, trigger_time),
                enable_custom_actions=False,
            )
        )

    def _gaps_to_report(
        self,
        gaps: list[CoverageGap],
        trigger_time: datetime | None,
        retry_from: datetime | None,
    ) -> list[CoverageGap]:
        """Return the gaps to report on this run, or none if no report is due."""
        if trigger_time is None:
            return gaps

        interval = timedelta(minutes=self.run_interval_minutes)
        trigger_utc = trigger_time.astimezone(UTC)
        is_digest_run = (
            trigger_utc.hour == self.DIGEST_HOUR_UTC
            and trigger_utc.minute < self.run_interval_minutes
        )

        reportable = []
        newly_reportable = False
        for gap in gaps:
            retrying = retry_from is not None and gap.start >= retry_from
            grace = interval * self.RETRY_GRACE_RUNS if retrying else timedelta(0)
            if gap.stranded_at is None:
                # Age unknown, so it cannot be throttled: always report it.
                reportable.append(gap)
                newly_reportable = True
                continue
            age = trigger_time - gap.stranded_at
            if age < grace:
                continue
            reportable.append(gap)
            if age < grace + interval:
                newly_reportable = True

        return reportable if newly_reportable or is_digest_run else []

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
            f"Found *{total_gaps} coverage gap(s)* totaling "
            f"*{total_missing_hours:.1f} hours* of missing windows.",
            "",
            f"*Table:* `{self.table_name}`",
        ]

        if job_id:
            lines.append(f"*Job ID:* `{job_id}`")

        if trigger_time:
            lines.append(
                f"*Triggered:* {trigger_time.strftime('%Y-%m-%d %H:%M:%S UTC')}"
            )

        lines.extend(["", "*Coverage Gaps:*"])

        gaps_to_show = report.coverage_gaps[: self.MAX_GAPS_TO_DISPLAY]
        for i, gap in enumerate(gaps_to_show, 1):
            duration_hours = (gap.end - gap.start).total_seconds() / 3600.0
            lines.append(
                f"{i}. *{gap.start.strftime('%Y-%m-%d %H:%M')}* → "
                f"*{gap.end.strftime('%Y-%m-%d %H:%M')}* "
                f"({duration_hours:.1f}h)"
            )

        if total_gaps > self.MAX_GAPS_TO_DISPLAY:
            remaining = total_gaps - self.MAX_GAPS_TO_DISPLAY
            lines.append(f"\n*...and {remaining} more gap(s)*")

        return "\n".join(lines)

    def _build_next_steps(
        self,
        report: WindowCoverageReport,
        job_id: str | None,
    ) -> list[str] | None:
        """Build next steps instructions for gap remediation.

        Args:
            report: Window coverage report containing gap information.
            job_id: Optional job identifier.

        Returns:
            List of next step instructions, or None if no gaps.
        """
        if not report.coverage_gaps:
            return None

        first_gap = report.coverage_gaps[0]
        last_gap = report.coverage_gaps[-1]

        # Format ISO timestamps for command-line usage
        window_start = first_gap.start.strftime("%Y-%m-%dT%H:%M:%SZ")
        window_end = last_gap.end.strftime("%Y-%m-%dT%H:%M:%SZ")

        execution_input = json.dumps(
            {
                "adapter_type": self.adapter_name,
                "window": {"start_time": window_start, "end_time": window_end},
            }
        )

        return [
            f"Start an execution of the `{self.adapter_name}-adapter` state machine "
            f"with this input to backfill the range:\n```json\n{execution_input}\n```",
            "The run skips windows that are already published and sends what it "
            "harvests on to the transformer",
        ]

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

        if report.last_published_end:
            context["last_published_end"] = report.last_published_end.isoformat()

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
