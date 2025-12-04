from __future__ import annotations

from collections import Counter
from datetime import UTC, datetime, timedelta

from pydantic import BaseModel, Field

from utils.timezone import ensure_datetime_utc

from .window_store import WindowStore
from .window_summary import WindowSummary


class CoverageGap(BaseModel):
    start: datetime
    end: datetime


class WindowFailure(BaseModel):
    window_key: str
    window_start: datetime
    window_end: datetime
    attempts: int
    last_error: str | None


class WindowCoverageReport(BaseModel):
    range_start: datetime
    range_end: datetime
    total_windows: int = Field(0, ge=0)
    state_counts: dict[str, int] = Field(default_factory=dict)
    coverage_hours: float = 0.0
    coverage_gaps: list[CoverageGap] = Field(default_factory=list)
    failures: list[WindowFailure] = Field(default_factory=list)
    last_success_end: datetime | None = None

    def summary(self) -> str:
        """Generate a human-readable summary for logging."""
        lines = [
            f"Window Coverage Report ({self.range_start.isoformat()} to {self.range_end.isoformat()})",
            f"Total windows: {self.total_windows}",
        ]

        if self.state_counts:
            state_summary = ", ".join(
                f"{state}: {count}"
                for state, count in sorted(self.state_counts.items())
            )
            lines.append(f"States: {state_summary}")

        lines.append(f"Coverage: {self.coverage_hours:.2f} hours")

        if self.coverage_gaps:
            lines.append(f"Gaps: {len(self.coverage_gaps)}")
            for gap in self.coverage_gaps[:3]:  # Show first 3 gaps
                duration = (gap.end - gap.start).total_seconds() / 3600
                lines.append(
                    f"  - {gap.start.isoformat()} to {gap.end.isoformat()} ({duration:.2f}h)"
                )
            if len(self.coverage_gaps) > 3:
                lines.append(f"  ... and {len(self.coverage_gaps) - 3} more")
        else:
            lines.append("Gaps: none")

        if self.failures:
            lines.append(f"Failures: {len(self.failures)}")
            for failure in self.failures[:3]:  # Show first 3 failures
                lines.append(f"  - {failure.window_key} (attempts: {failure.attempts})")
                if failure.last_error:
                    error_preview = (
                        failure.last_error[:80] + "..."
                        if len(failure.last_error) > 80
                        else failure.last_error
                    )
                    lines.append(f"    Error: {error_preview}")
            if len(self.failures) > 3:
                lines.append(f"  ... and {len(self.failures) - 3} more")
        else:
            lines.append("Failures: none")

        if self.last_success_end:
            lines.append(
                f"Last successful window ended: {self.last_success_end.isoformat()}"
            )

        return "\n".join(lines)


class WindowReporter:
    """Generates coverage reports for window processing history.

    Analyzes the state and timing of data processing windows to identify:
    - Coverage gaps (time periods with no successful processing)
    - Failed windows (windows that did not complete successfully)
    - Overall coverage percentage
    """

    def __init__(self, store: WindowStore, window_minutes: int = 15) -> None:
        self.store = store
        self.window_minutes = window_minutes

    def coverage_report(
        self,
        *,
        range_start: datetime | None = None,
        range_end: datetime | None = None,
    ) -> WindowCoverageReport:
        """Generate a comprehensive coverage report for a time range.

        Args:
            range_start: Start of the time range to analyze. If None, uses the earliest
                window's start time (or current time if no windows exist).
            range_end: End of the time range to analyze. If None, uses the latest
                window's end time (or current time if no windows exist).

        Returns:
            WindowCoverageReport containing:
            - total_windows: Count of windows in the range
            - state_counts: Distribution of window states (success, failed, etc.)
            - coverage_hours: Hours of successful processing coverage (accounts for overlaps)
            - coverage_gaps: Periods with no successful coverage
            - failures: Details of failed windows
            - last_success_end: When the last successful window ended
        """
        rows = self._rows_in_range(range_start, range_end)

        # Handle case where no windows exist in range
        if not rows:
            return self._empty_range_report(range_start, range_end)

        # Sort windows by start time for deterministic processing
        sorted_rows = sorted(
            rows, key=lambda row: ensure_datetime_utc(row.window_start)
        )

        # Determine the overall range boundaries
        first_start = (
            ensure_datetime_utc(range_start)
            if range_start
            else ensure_datetime_utc(sorted_rows[0].window_start)
        )
        last_end = (
            ensure_datetime_utc(range_end)
            if range_end
            else ensure_datetime_utc(sorted_rows[-1].window_end)
        )

        # Extract successful windows and calculate coverage
        state_counts = Counter(row.state for row in sorted_rows)
        successful_rows = [row for row in sorted_rows if row.state == "success"]

        coverage_hours = self._calculate_merged_coverage(
            successful_rows, first_start, last_end
        )

        # Identify gaps in coverage
        coverage_gaps = self._calculate_coverage_gaps(
            successful_rows, first_start, last_end
        )

        # Extract failure details
        failures = self._extract_failures(sorted_rows)

        # Track the last successful window end time
        last_success_end = (
            max(ensure_datetime_utc(row.window_end) for row in successful_rows)
            if successful_rows
            else None
        )

        return WindowCoverageReport(
            range_start=first_start,
            range_end=last_end,
            total_windows=len(sorted_rows),
            state_counts=dict(state_counts),
            coverage_hours=coverage_hours,
            coverage_gaps=coverage_gaps,
            failures=failures,
            last_success_end=last_success_end,
        )

    def _empty_range_report(
        self,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> WindowCoverageReport:
        """Create a report for an empty range (no windows found).

        Args:
            range_start: Requested start of range
            range_end: Requested end of range

        Returns:
            WindowCoverageReport with zero windows and the entire range as a gap
        """
        now = datetime.now(UTC)
        start = ensure_datetime_utc(range_start) if range_start else now
        end = ensure_datetime_utc(range_end) if range_end else now
        gaps = [CoverageGap(start=start, end=end)] if start < end else []
        return WindowCoverageReport(
            range_start=start,
            range_end=end,
            total_windows=0,
            coverage_gaps=gaps,
        )

    def _calculate_merged_coverage(
        self,
        successful_rows: list,
        range_start: datetime,
        range_end: datetime,
    ) -> float:
        """Calculate coverage hours accounting for overlapping windows.

        Merges overlapping successful windows to avoid double-counting coverage.
        For example, two windows [12:00-12:15] and [12:00-13:00] represent 1 hour
        of coverage, not 1.25 hours.

        Args:
            successful_rows: List of successful WindowSummary objects, must be sorted by start time
            range_start: Start of the analysis range
            range_end: End of the analysis range

        Returns:
            Total coverage hours (float)
        """
        if not successful_rows:
            return 0.0

        # Convert and clip each window to the analysis range
        clipped_intervals: list[tuple[datetime, datetime]] = []
        for row in successful_rows:
            window_start = ensure_datetime_utc(row.window_start)
            window_end = ensure_datetime_utc(row.window_end)

            # Clip to range boundaries
            clipped_start = max(window_start, range_start)
            clipped_end = min(window_end, range_end)

            # Only include if there's actual overlap with range
            if clipped_start < clipped_end:
                clipped_intervals.append((clipped_start, clipped_end))

        if not clipped_intervals:
            return 0.0

        # Merge overlapping intervals
        merged_intervals = self._merge_intervals(clipped_intervals)

        # Sum the duration of merged intervals
        total_seconds = sum(
            (end - start).total_seconds() for start, end in merged_intervals
        )

        return total_seconds / 3600.0

    @staticmethod
    def _merge_intervals(
        intervals: list[tuple[datetime, datetime]],
    ) -> list[tuple[datetime, datetime]]:
        """Merge overlapping or adjacent time intervals.

        Args:
            intervals: List of (start, end) datetime tuples, assumed sorted by start time

        Returns:
            List of merged non-overlapping intervals
        """
        if not intervals:
            return []

        merged: list[tuple[datetime, datetime]] = [intervals[0]]

        for start, end in intervals[1:]:
            last_start, last_end = merged[-1]

            if start <= last_end:
                # Overlapping or adjacent: extend the last interval
                merged[-1] = (last_start, max(last_end, end))
            else:
                # Gap: add a new interval
                merged.append((start, end))

        return merged

    def _calculate_coverage_gaps(
        self,
        successful_rows: list,
        range_start: datetime,
        range_end: datetime,
    ) -> list[CoverageGap]:
        """Identify time periods with no successful processing.

        Args:
            successful_rows: List of successful WindowSummary objects, must be sorted by start time
            range_start: Start of the analysis range
            range_end: End of the analysis range

        Returns:
            List of CoverageGap objects representing periods with no coverage
        """
        coverage_gaps: list[CoverageGap] = []

        if not successful_rows:
            # No successful windows: entire range is a gap
            if range_start < range_end:
                coverage_gaps.append(CoverageGap(start=range_start, end=range_end))
            return coverage_gaps

        # Convert successful windows to (start, end) tuples
        successful_intervals = [
            (
                ensure_datetime_utc(row.window_start),
                ensure_datetime_utc(row.window_end),
            )
            for row in successful_rows
        ]

        # Merge overlapping successful intervals
        merged_intervals = self._merge_intervals(successful_intervals)

        # Find gaps before the first successful window
        first_start = merged_intervals[0][0]
        if range_start < first_start:
            coverage_gaps.append(CoverageGap(start=range_start, end=first_start))

        # Find gaps between successful windows
        for i in range(len(merged_intervals) - 1):
            gap_start = merged_intervals[i][1]
            gap_end = merged_intervals[i + 1][0]
            if gap_start < gap_end:
                coverage_gaps.append(CoverageGap(start=gap_start, end=gap_end))

        # Find gap after the last successful window
        last_end = merged_intervals[-1][1]
        if last_end < range_end:
            coverage_gaps.append(CoverageGap(start=last_end, end=range_end))

        return coverage_gaps

    def _extract_failures(self, sorted_rows: list) -> list[WindowFailure]:
        """Extract failure details from all windows.

        Args:
            sorted_rows: List of WindowSummary objects sorted by start time

        Returns:
            List of WindowFailure objects for non-successful windows
        """
        return [
            WindowFailure(
                window_key=row.window_key,
                window_start=ensure_datetime_utc(row.window_start),
                window_end=ensure_datetime_utc(row.window_end),
                attempts=row.attempts,
                last_error=row.last_error,
            )
            for row in sorted_rows
            if row.state != "success"
        ]

    def _rows_in_range(
        self,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> list[WindowSummary]:
        """Fetch windows that overlap with the requested time range.

        This method looks back further than the requested range_start to catch
        long-running windows that may have started before range_start but overlap
        with it. Specifically, it looks back by max(window_minutes * 2, 1440 minutes).

        Args:
            range_start: Requested start of range (or None for unbounded start)
            range_end: Requested end of range (or None for unbounded end)

        Returns:
            List of WindowSummary objects that overlap with the requested range
        """
        start_bound = ensure_datetime_utc(range_start) if range_start else None
        end_bound = ensure_datetime_utc(range_end) if range_end else None

        search_start = None
        if start_bound:
            # Look back 24 hours to catch any long windows or windows that started much earlier
            # but overlap with the requested range.
            lookback_minutes = max(self.window_minutes * 2, 1440)
            search_start = start_bound - timedelta(minutes=lookback_minutes)

        raw_rows = self.store.list_in_range(start=search_start, end=end_bound)

        rows: list[WindowSummary] = []
        for raw_row in raw_rows:
            typed_row = WindowSummary.model_validate(raw_row)
            if self._within_range(
                typed_row.window_start,
                typed_row.window_end,
                start_bound,
                end_bound,
            ):
                rows.append(typed_row)
        return rows

    @staticmethod
    def _within_range(
        window_start: datetime,
        window_end: datetime,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> bool:
        """Check if a window overlaps with the requested time range.

        A window overlaps if:
        - It ends after (or at) the range start, AND
        - It starts before (or at) the range end

        Args:
            window_start: Start of the window
            window_end: End of the window
            range_start: Requested range start (or None for unbounded)
            range_end: Requested range end (or None for unbounded)

        Returns:
            True if the window overlaps with the range
        """
        if range_start and window_end <= ensure_datetime_utc(range_start):
            return False
        return not (range_end and window_start >= ensure_datetime_utc(range_end))
