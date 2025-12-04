from __future__ import annotations

import logging
from datetime import UTC, datetime, timedelta

from utils.timezone import ensure_datetime_utc

logger = logging.getLogger(__name__)

ALIGNMENT_EPOCH = datetime(1970, 1, 1, tzinfo=UTC)
DEFAULT_WINDOW_MINUTES = 15


class WindowGenerator:
    """Generates time windows aligned to fixed boundaries."""

    def __init__(
        self,
        window_minutes: int | None = None,
        *,
        allow_partial_final_window: bool = True,
    ) -> None:
        """Initialize the window generator.

        Args:
            window_minutes: Size of each window in minutes. Defaults to 15.
            allow_partial_final_window: If False, truncate end_time to the previous
                aligned boundary to avoid creating windows smaller than window_minutes.
                If True (default), allow the final window to be partial.
        """
        self.window_minutes = window_minutes or DEFAULT_WINDOW_MINUTES
        self.allow_partial_final_window = allow_partial_final_window

    def generate_windows(
        self, start_time: datetime, end_time: datetime
    ) -> list[tuple[datetime, datetime]]:
        """Generate aligned time windows between start_time and end_time.

        Windows are aligned to fixed boundaries based on ALIGNMENT_EPOCH and
        window_minutes. For example, with 15-minute windows, boundaries occur
        at :00, :15, :30, :45 of each hour.

        Args:
            start_time: Start of the time range (inclusive).
            end_time: End of the time range (exclusive).

        Returns:
            List of (window_start, window_end) tuples.

        Raises:
            ValueError: If start_time >= end_time.
        """
        start_time = ensure_datetime_utc(start_time)
        end_time = ensure_datetime_utc(end_time)

        if start_time >= end_time:
            raise ValueError("start_time must be earlier than end_time")

        delta = timedelta(minutes=self.window_minutes)
        effective_end_time = end_time

        # Align end_time to the previous boundary to avoid partial windows
        if not self.allow_partial_final_window:
            offset_from_epoch = end_time - ALIGNMENT_EPOCH
            periods_until_end = offset_from_epoch // delta
            aligned_end_time = ALIGNMENT_EPOCH + periods_until_end * delta

            # If aligned_end_time would be before start_time, use the next boundary
            if aligned_end_time < start_time:
                aligned_end_time = ALIGNMENT_EPOCH + (periods_until_end + 1) * delta

            effective_end_time = aligned_end_time

        windows: list[tuple[datetime, datetime]] = []
        cursor = start_time

        while cursor < effective_end_time:
            offset = cursor - ALIGNMENT_EPOCH
            periods = offset // delta
            aligned_window_end = ALIGNMENT_EPOCH + (periods + 1) * delta
            win_end = min(aligned_window_end, effective_end_time)
            windows.append((cursor, win_end))
            cursor = win_end

        logger.info(
            "Generated %d windows covering %s -> %s (size=%d minutes, allow_partial=%s)",
            len(windows),
            start_time.isoformat(),
            effective_end_time.isoformat(),
            self.window_minutes,
            self.allow_partial_final_window,
        )

        return windows
