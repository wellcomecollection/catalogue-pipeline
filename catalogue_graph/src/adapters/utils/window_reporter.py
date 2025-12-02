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


class WindowReporter:
    def __init__(self, store: WindowStore, window_minutes: int = 15) -> None:
        self.store = store
        self.window_minutes = window_minutes

    def coverage_report(
        self,
        *,
        range_start: datetime | None = None,
        range_end: datetime | None = None,
    ) -> WindowCoverageReport:
        rows = self._rows_in_range(range_start, range_end)
        if not rows:
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
        sorted_rows = sorted(
            rows, key=lambda row: ensure_datetime_utc(row.window_start)
        )
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
        state_counts = Counter(row.state for row in sorted_rows)
        successful_rows = [row for row in sorted_rows if row.state == "success"]
        coverage_hours = (
            sum(
                max(
                    0.0,
                    (
                        min(ensure_datetime_utc(row.window_end), last_end)
                        - max(ensure_datetime_utc(row.window_start), first_start)
                    ).total_seconds(),
                )
                for row in successful_rows
            )
            / 3600.0
        )
        coverage_gaps: list[CoverageGap] = []

        if not successful_rows:
            if first_start < last_end:
                coverage_gaps.append(CoverageGap(start=first_start, end=last_end))
        else:
            # Check for gap at the start
            first_window_start = ensure_datetime_utc(successful_rows[0].window_start)
            if first_start < first_window_start:
                coverage_gaps.append(
                    CoverageGap(start=first_start, end=first_window_start)
                )

            rolling_end = ensure_datetime_utc(successful_rows[0].window_end)
            for row in successful_rows[1:]:
                start = ensure_datetime_utc(row.window_start)
                end = ensure_datetime_utc(row.window_end)
                if start > rolling_end:
                    coverage_gaps.append(CoverageGap(start=rolling_end, end=start))
                    rolling_end = end
                else:
                    rolling_end = max(rolling_end, end)

            # Check for gap at the end
            if rolling_end < last_end:
                coverage_gaps.append(CoverageGap(start=rolling_end, end=last_end))

        failures = [
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

    def _rows_in_range(
        self,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> list[WindowSummary]:
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
        if range_start and window_end <= ensure_datetime_utc(range_start):
            return False
        return not (range_end and window_start >= ensure_datetime_utc(range_end))
