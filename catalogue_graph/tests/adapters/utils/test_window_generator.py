from __future__ import annotations

from datetime import UTC, datetime

import pytest

from adapters.utils.window_generator import WindowGenerator


@pytest.mark.parametrize(
    "window_minutes, allow_partial, expected",
    [
        # With allow_partial_final_window=True (default behavior)
        (
            15,
            True,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 45, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 45, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        # With allow_partial_final_window=False (no partial windows)
        (
            15,
            False,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 45, tzinfo=UTC),
                ),
                # Last window (13:45-13:55) is excluded because it's only 10 minutes
            ],
        ),
        (
            30,
            True,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            30,
            False,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                # Last window (13:30-13:55) is excluded because it's only 25 minutes
            ],
        ),
        (
            60,
            True,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            60,
            False,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                # Last window (13:00-13:55) is excluded because it's only 55 minutes
            ],
        ),
        (
            240,
            True,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            240,
            False,
            [
                # With 240-minute windows, the aligned boundary before 13:55 is 12:00,
                # which is before start_time 12:07, so we use the next boundary (16:00).
                # This creates a single window from 12:07 to 16:00.
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 16, 0, tzinfo=UTC),
                ),
            ],
        ),
    ],
)
def test_generate_windows_aligns_to_boundaries(
    window_minutes: int,
    allow_partial: bool,
    expected: list[tuple[datetime, datetime]],
) -> None:
    generator = WindowGenerator(
        window_minutes=window_minutes,
        allow_partial_final_window=allow_partial,
    )
    start = datetime(2025, 1, 1, 12, 7, tzinfo=UTC)
    end = datetime(2025, 1, 1, 13, 55, tzinfo=UTC)

    windows = generator.generate_windows(start, end)

    assert windows == expected


def test_generate_windows_validates_time_range() -> None:
    generator = WindowGenerator()
    end_time = datetime(2025, 1, 1, tzinfo=UTC)

    with pytest.raises(ValueError, match="start_time must be earlier than end_time"):
        generator.generate_windows(end_time, end_time)


def test_generate_windows_with_aligned_boundaries() -> None:
    """When start and end are already aligned, behavior is consistent."""
    generator = WindowGenerator(window_minutes=15, allow_partial_final_window=False)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)
    end = datetime(2025, 1, 1, 13, 0, tzinfo=UTC)

    windows = generator.generate_windows(start, end)

    assert len(windows) == 4
    assert windows[0] == (
        datetime(2025, 1, 1, 12, 0, tzinfo=UTC),
        datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
    )
    assert windows[-1] == (
        datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
        datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
    )


def test_generate_windows_single_full_window() -> None:
    """A single complete window is generated correctly."""
    generator = WindowGenerator(window_minutes=60, allow_partial_final_window=False)
    start = datetime(2025, 1, 1, 12, 30, tzinfo=UTC)
    end = datetime(2025, 1, 1, 13, 45, tzinfo=UTC)

    windows = generator.generate_windows(start, end)

    assert len(windows) == 1
    assert windows[0] == (
        datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
        datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
    )


def test_default_window_minutes() -> None:
    """Verify default window size is 15 minutes."""
    generator = WindowGenerator()
    assert generator.window_minutes == 15


def test_default_allow_partial_final_window() -> None:
    """Verify default allows partial final windows."""
    generator = WindowGenerator()
    assert generator.allow_partial_final_window is True
