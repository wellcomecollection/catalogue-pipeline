"""Tests for the id-minter work-discovery step."""

from __future__ import annotations

import pytest

from id_minter.config import ID_MINTER_CONFIG
from id_minter.models.step_events import (
    MintingFindWorkEvent,
    StepFunctionMintingRequest,
)
from id_minter.steps import find_work
from tests.mocks import MockElasticsearchClient, mock_es_secrets
from utils.aws import pydantic_from_s3_json


class _LambdaContext:
    aws_request_id = "req-abc123"


PIPELINE_DATE = ID_MINTER_CONFIG.pipeline_date  # "dev" unless env overrides


def _seed_source_works(ids: list[str]) -> None:
    for work_id in ids:
        MockElasticsearchClient.index(
            index=ID_MINTER_CONFIG.source_index_name,
            id=work_id,
            document={"indexed_at": "2026-07-30T16:33:00Z"},
        )


def test_handler_partitions_with_per_partition_job_ids() -> None:
    mock_es_secrets("id_minter", PIPELINE_DATE)
    _seed_source_works(["a", "b", "c"])

    event = MintingFindWorkEvent(
        pipeline_date=PIPELINE_DATE,
        graph_date=PIPELINE_DATE,
        partition_size=2,
        job_id="replay-s01",
    )
    result = find_work.handler(event, es_mode="private")

    assert len(result.partitions) == 2
    assert [p.job_id for p in result.partitions] == [
        "replay-s01-p000",
        "replay-s01-p001",
    ]
    all_ids = [i for p in result.partitions for i in (p.source_identifiers or [])]
    assert sorted(all_ids) == ["a", "b", "c"]


def test_lambda_handler_writes_partitions_to_s3_and_returns_refs() -> None:
    mock_es_secrets("id_minter", PIPELINE_DATE)
    _seed_source_works(["a", "b", "c"])

    event = {
        "pipeline_date": PIPELINE_DATE,
        "graph_date": PIPELINE_DATE,
        "partition_size": 2,
        "window": {
            "start_time": "2026-07-30T16:32:00Z",
            "end_time": "2026-07-30T16:34:00Z",
        },
    }
    result = find_work.lambda_handler(event, _LambdaContext())

    refs = result["partitions"]
    assert len(refs) == 2
    assert sum(r["count"] for r in refs) == 3
    # Scope-keyed under the id_minter service prefix, so a rerun of the same
    # window overwrites rather than accumulates.
    assert all(
        "/id_minter/find_work/windows/20260730T1632-20260730T1634/" in r["s3_uri"]
        for r in refs
    )

    # The refs resolve back to full minting requests with distinct job ids.
    resolved = [
        pydantic_from_s3_json(StepFunctionMintingRequest, r["s3_uri"]) for r in refs
    ]
    resolved_ids = [i for p in resolved if p for i in (p.source_identifiers or [])]
    assert sorted(resolved_ids) == ["a", "b", "c"]
    job_ids = [p.job_id for p in resolved if p]
    assert len(set(job_ids)) == 2


def test_lambda_handler_accepts_scheduled_time(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PIPELINE_DATE", PIPELINE_DATE)
    monkeypatch.setenv("GRAPH_DATE", PIPELINE_DATE)
    mock_es_secrets("id_minter", PIPELINE_DATE)
    _seed_source_works(["a"])

    # scheduled_time - 5min lag = window end 16:35; start defaults to 16:20,
    # covering the seeded indexed_at of 16:33.
    result = find_work.lambda_handler(
        {"scheduled_time": "2026-07-30T16:40:00Z"}, _LambdaContext()
    )
    assert len(result["partitions"]) == 1


def test_handler_builds_window_query_on_indexed_at() -> None:
    mock_es_secrets("id_minter", PIPELINE_DATE)
    _seed_source_works(["a"])

    event = MintingFindWorkEvent.model_validate(
        {
            "pipeline_date": PIPELINE_DATE,
            "graph_date": PIPELINE_DATE,
            "window": {"end_time": "2026-07-30T16:34:00Z"},
        }
    )
    find_work.handler(event, es_mode="private")

    assert MockElasticsearchClient.queries[-1] == {
        "range": {
            "indexed_at": {
                "gte": "2026-07-30T16:19:00+00:00",
                "lte": "2026-07-30T16:34:00+00:00",
            }
        }
    }
