"""The merge group step: merges one group of changed components and writes the results."""

from datetime import UTC, datetime
from typing import cast
from unittest.mock import MagicMock

import polars as pl
import pytest
from elasticsearch import Elasticsearch

from merger.models.step_events import MergeGroupEvent
from merger.steps import merge_group
from models.pipeline.format import ThreeDObjects
from models.pipeline.identified.work import IdentifiedWork
from tests.merger.works import (
    MIRO_SIERRA_REASON,
    merge_candidate,
    miro_work,
    sierra_physical_work,
    sierra_work,
)
from tests.mocks import MockElasticsearchClient, MockSmartOpen
from utils.elasticsearch import version_from_datetime

MERGED_TIME = datetime(2026, 11, 17, 9, 30, tzinfo=UTC)
EVENT = MergeGroupEvent(
    pipeline_date="2026-01-01",
    graph_date="2026-01-02",
    job_id="job-1",
    iceberg_snapshot_id=7,
    group=1,
    merged_time=MERGED_TIME,
)

miro = miro_work()
sierra = sierra_physical_work(
    format=ThreeDObjects, merge_candidates=[merge_candidate(miro, MIRO_SIERRA_REASON)]
)
lone = sierra_work()
other_group = sierra_work()
WORKS = {w.state.canonical_id: w for w in (miro, sierra, lone, other_group)}


def changed_components(*groups: tuple[int, str, list[IdentifiedWork]]) -> None:
    rows = [
        (work.state.canonical_id, component_id, group)
        for group, component_id, works in groups
        for work in works
    ]
    frame = pl.DataFrame(
        rows,
        schema={"id": pl.Utf8, "component_id": pl.Utf8, "group": pl.Int32},
        orient="row",
    )
    MockSmartOpen.mock_s3_parquet_file(EVENT.get_s3_uri("changed_components"), frame)


def run(
    monkeypatch: pytest.MonkeyPatch, missing: set[str] | None = None
) -> tuple[merge_group.MergeGroupResult, list[pl.DataFrame]]:
    """Runs the step with the given works in the table, less any missing ones."""
    fetched: list[pl.DataFrame] = []

    def fetch(
        table: object, snapshot_id: int, changed: pl.DataFrame
    ) -> list[tuple[list[str], list[IdentifiedWork]]]:
        assert snapshot_id == EVENT.iceberg_snapshot_id
        fetched.append(changed)
        members = changed.group_by("component_id", maintain_order=True).agg("id")
        return [
            (list(ids), [WORKS[i] for i in ids if i not in (missing or set())])
            for ids in members["id"]
        ]

    monkeypatch.setattr(merge_group, "get_works_identified_table", MagicMock())
    monkeypatch.setattr(merge_group, "fetch_components", fetch)
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
    return merge_group.handler(EVENT, es_client=es_client), fetched


def written(index: str) -> dict[str, dict]:
    return {
        op["_id"]: op for op in MockElasticsearchClient.inputs if op["_index"] == index
    }


@pytest.fixture(autouse=True)
def changed() -> None:
    changed_components(
        (1, "c1", [sierra, miro]), (1, "c2", [lone]), (2, "c3", [other_group])
    )


def test_merges_only_the_components_of_its_group(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    result, fetched = run(monkeypatch)

    [frame] = fetched
    assert set(frame["component_id"]) == {"c1", "c2"}
    assert (result.component_count, result.work_count) == (2, 3)
    assert (result.merged_work_count, result.image_count) == (3, 1)


def test_writes_merged_works_and_images_to_their_own_indices(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    run(monkeypatch)

    works = written("works-denormalised-python-2026-01-01")
    assert set(works) == {
        sierra.state.canonical_id,
        miro.state.canonical_id,
        lone.state.canonical_id,
    }
    assert works[miro.state.canonical_id]["_source"]["type"] == "Redirected"
    assert works[sierra.state.canonical_id]["_source"]["state"]["mergedTime"] == (
        "2026-11-17T09:30:00Z"
    )
    images = written("images-initial-python-2026-01-01")
    [image] = miro.data.image_data
    assert set(images) == {image.id.canonical_id}
    assert images[image.id.canonical_id]["_source"]["source"]["id"]["canonicalId"] == (
        sierra.state.canonical_id
    )


def test_every_document_is_versioned_by_the_snapshot_time(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    run(monkeypatch)

    assert {op["_version"] for op in MockElasticsearchClient.inputs} == {
        version_from_datetime(MERGED_TIME)
    }
    assert {op["_version_type"] for op in MockElasticsearchClient.inputs} == {
        "external_gte"
    }


def test_a_component_with_a_missing_work_is_written_unmerged(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    run(monkeypatch, missing={miro.state.canonical_id})

    works = written("works-denormalised-python-2026-01-01")
    assert set(works) == {sierra.state.canonical_id, lone.state.canonical_id}
    assert (
        works[sierra.state.canonical_id]["_source"]["data"]["items"]
        == (sierra.model_dump(mode="json", exclude_none=True)["data"]["items"])
    )
    assert written("images-initial-python-2026-01-01") == {}


def test_a_document_written_by_a_later_run_is_not_a_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockElasticsearchClient.bulk_errors = [
        {
            "index": {
                "_id": lone.state.canonical_id,
                "error": {"type": "version_conflict_engine_exception"},
            }
        }
    ]

    result, _ = run(monkeypatch)

    assert result.merged_work_count == 3


def test_a_failed_write_fails_the_group(monkeypatch: pytest.MonkeyPatch) -> None:
    MockElasticsearchClient.bulk_errors = [
        {
            "index": {
                "_id": lone.state.canonical_id,
                "error": {"type": "mapper_parsing_exception"},
            }
        }
    ]

    with pytest.raises(RuntimeError, match="1 documents failed to index"):
        run(monkeypatch)
