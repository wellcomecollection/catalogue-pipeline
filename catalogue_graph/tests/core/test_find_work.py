"""Tests for the shared find_work Lambda input normalisation."""

from __future__ import annotations

import pytest

from core.find_work import normalise_lambda_input

DEFAULTS = {"pipeline_date": "dev", "graph_date": "dev"}


def test_scheduled_time_becomes_window_end_minus_lag() -> None:
    data = normalise_lambda_input({"scheduled_time": "2026-08-04T14:20:45Z"}, DEFAULTS)
    assert data["window"]["end_time"].isoformat() == "2026-08-04T14:15:45+00:00"
    assert data["pipeline_date"] == "dev"
    assert "scheduled_time" not in data


def test_explicit_window_wins_over_scheduled_time() -> None:
    window = {"start_time": "2026-08-01T00:00:00Z", "end_time": "2026-08-01T01:00:00Z"}
    data = normalise_lambda_input(
        {"scheduled_time": "2026-08-04T14:20:45Z", "window": window}, DEFAULTS
    )
    assert data["window"] == window


def test_source_identifiers_is_an_alias_for_ids() -> None:
    data = normalise_lambda_input({"source_identifiers": ["a", "b"]}, DEFAULTS)
    assert data["ids"] == ["a", "b"]
    assert "source_identifiers" not in data


def test_ids_win_over_the_alias() -> None:
    data = normalise_lambda_input({"ids": ["a"], "source_identifiers": ["b"]}, DEFAULTS)
    assert data["ids"] == ["a"]


def test_no_scope_requires_explicit_full() -> None:
    with pytest.raises(ValueError, match="full"):
        normalise_lambda_input({"job_id": "x"}, DEFAULTS)


def test_null_window_does_not_become_full_scan() -> None:
    with pytest.raises(ValueError, match="full"):
        normalise_lambda_input({"window": None}, DEFAULTS)


def test_full_flag_allows_unscoped() -> None:
    data = normalise_lambda_input({"full": True}, DEFAULTS)
    assert "window" not in data and "ids" not in data and "full" not in data


def test_input_identity_wins_over_defaults() -> None:
    data = normalise_lambda_input(
        {"ids": ["a"], "pipeline_date": "2026-07-03"}, DEFAULTS
    )
    assert data["pipeline_date"] == "2026-07-03"
    assert data["graph_date"] == "dev"
