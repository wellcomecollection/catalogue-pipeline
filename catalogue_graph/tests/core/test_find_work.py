"""Tests for the shared find_work input normalisation and partition hand-off."""

from __future__ import annotations

import pytest
from pydantic import BaseModel

from core.find_work import (
    group_by_weight,
    normalise_lambda_input,
    plan_partitions,
    write_partitions_to_s3,
)
from utils.aws import pydantic_from_s3_json

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


class _Partition(BaseModel):
    ids: list[str]
    job_id: str


def test_plan_partitions_slices_ids_and_passes_each_index() -> None:
    partitions = plan_partitions(
        ["a", "b", "c", "d", "e"],
        2,
        lambda chunk, index: _Partition(ids=chunk, job_id=f"job-p{index:03d}"),
    )

    assert [p.ids for p in partitions] == [["a", "b"], ["c", "d"], ["e"]]
    assert [p.job_id for p in partitions] == ["job-p000", "job-p001", "job-p002"]


def test_plan_partitions_of_nothing_is_empty() -> None:
    partitions = plan_partitions(
        [], 10, lambda chunk, index: _Partition(ids=chunk, job_id="job")
    )

    assert partitions == []


def test_write_partitions_to_s3_uses_the_callers_uris() -> None:
    partitions = [
        _Partition(ids=["a", "b"], job_id="job-p000"),
        _Partition(ids=["c"], job_id="job-p001"),
    ]

    refs = write_partitions_to_s3(
        partitions,
        [2, 1],
        lambda index: f"s3://test-bucket/plans/job/partition-{index}.json",
    )

    assert [(r.s3_uri, r.count) for r in refs] == [
        ("s3://test-bucket/plans/job/partition-0.json", 2),
        ("s3://test-bucket/plans/job/partition-1.json", 1),
    ]
    assert pydantic_from_s3_json(_Partition, refs[1].s3_uri) == partitions[1]


def test_group_by_weight_packs_in_order_up_to_the_limit() -> None:
    groups = group_by_weight(
        [("a", 4), ("b", 4), ("c", 3), ("d", 0), ("e", 9)], max_weight=8
    )

    assert groups == [["a", "b"], ["c", "d"], ["e"]]


def test_group_by_weight_gives_an_oversized_item_its_own_group() -> None:
    assert group_by_weight([("a", 2), ("big", 50), ("b", 2)], max_weight=10) == [
        ["a"],
        ["big"],
        ["b"],
    ]


def test_group_by_weight_of_nothing_is_empty() -> None:
    assert group_by_weight([], max_weight=10) == []
