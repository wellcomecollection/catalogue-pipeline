# -*- encoding: utf-8 -*-

import datetime as dt

import pytz

from build_missing_windows import get_missing_windows, sliding_window
from interval_arithmetic import Interval


def iv(start, end):
    return Interval(start=start, end=end, key=f"{start.isoformat()}__{end.isoformat()}")


def dtm(hour, minute, second=0):
    return dt.datetime(2000, 1, 1, hour, minute, second)


def utc(hour, minute, second=0):
    return dtm(hour, minute, second).replace(tzinfo=pytz.utc)


def test_sliding_window():
    assert list(sliding_window(range(5))) == [(0, 1), (1, 2), (2, 3), (3, 4)]


def test_no_windows_when_intervals_overlap():
    # interval_2 starts before interval_1 ends -> the time is already covered,
    # so there is no gap to re-harvest (regression test for phantom windows).
    report = [iv(dtm(12, 0), dtm(12, 5)), iv(dtm(12, 4), dtm(12, 10))]
    assert list(get_missing_windows(report)) == []


def test_no_windows_when_intervals_touch():
    # interval_2 starts exactly when interval_1 ends -> no uncovered time.
    report = [iv(dtm(12, 0), dtm(12, 5)), iv(dtm(12, 5), dtm(12, 10))]
    assert list(get_missing_windows(report)) == []


def test_emits_windows_for_a_real_gap():
    report = [iv(dtm(12, 0), dtm(12, 5)), iv(dtm(12, 10), dtm(12, 15))]
    windows = list(get_missing_windows(report))

    assert windows, "expected windows covering the 12:05-12:10 gap"
    # The gap start is padded by one second.
    assert windows[0]["start"] == utc(12, 4, 59).isoformat()

    # The generated windows span the whole gap.
    starts = [dt.datetime.fromisoformat(w["start"]) for w in windows]
    ends = [dt.datetime.fromisoformat(w["end"]) for w in windows]
    assert min(starts) <= utc(12, 5)
    assert max(ends) >= utc(12, 10)


def test_unsorted_input_is_handled():
    # Same gap as above, but supplied out of order: the merge sorts first.
    report = [iv(dtm(12, 10), dtm(12, 15)), iv(dtm(12, 0), dtm(12, 5))]
    windows = list(get_missing_windows(report))

    assert windows
    assert windows[0]["start"] == utc(12, 4, 59).isoformat()


def test_only_real_gaps_among_overlaps():
    # Gap between intervals 1 and 2; intervals 2 and 3 overlap (no gap there).
    report = [
        iv(dtm(12, 0), dtm(12, 5)),
        iv(dtm(12, 10), dtm(12, 20)),
        iv(dtm(12, 18), dtm(12, 30)),
    ]
    windows = list(get_missing_windows(report))

    assert windows
    # Every window belongs to the single 12:05-12:10 gap.
    starts = [dt.datetime.fromisoformat(w["start"]) for w in windows]
    assert all(s < utc(12, 11) for s in starts)


def test_respects_window_length():
    report = [iv(dtm(12, 0), dtm(12, 5)), iv(dtm(12, 10), dtm(12, 15))]
    windows = list(get_missing_windows(report, window_length_minutes=5))

    # Each window should be window_length minutes long.
    for w in windows:
        start = dt.datetime.fromisoformat(w["start"])
        end = dt.datetime.fromisoformat(w["end"])
        assert end - start == dt.timedelta(minutes=5)
