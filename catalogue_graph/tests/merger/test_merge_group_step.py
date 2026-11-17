"""The merge group step: merges one group of changed components and writes the results."""

from typing import cast
from unittest.mock import MagicMock

import polars as pl
import pytest
from elasticsearch import Elasticsearch

from merger.merge import MergedComponent
from merger.models.step_events import MergeGroupEvent
from merger.steps import merge_group
from models.pipeline.format import ThreeDObjects
from models.pipeline.identified.work import IdentifiedWork
from tests.merger.support import SNAPSHOT_TIME, iceberg_table
from tests.merger.works import (
    MIRO_SIERRA_REASON,
    merge_candidate,
    miro_work,
    sierra_physical_work,
    sierra_work,
)
from tests.mocks import MockElasticsearchClient, MockSmartOpen
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import version_from_datetime

EVENT = MergeGroupEvent(
    pipeline_date="2026-01-01",
    graph_date="2026-01-02",
    job_id="job-1",
    iceberg_snapshot_id=7,
    group=1,
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
    monkeypatch: pytest.MonkeyPatch,
    documents: dict[str, str] | None = None,
    table: MagicMock | None = None,
) -> tuple[merge_group.MergeGroupResult, set[str]]:
    """Runs the step against the table's documents, by default those of every work.
    Returns the result and the ids it read."""
    documents = documents or {i: w.model_dump_json() for i, w in WORKS.items()}
    read: set[str] = set()

    def read_documents(table: object, snapshot_id: int, ids: set[str]) -> dict:
        assert snapshot_id == EVENT.iceberg_snapshot_id
        read.update(ids)
        return {i: documents[i] for i in ids if i in documents}

    table = table or iceberg_table(pl.DataFrame(), EVENT.iceberg_snapshot_id)
    monkeypatch.setattr(
        merge_group, "get_works_identified_table", lambda use_rest: table
    )
    monkeypatch.setattr(merge_group, "read_documents", read_documents)
    es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
    return merge_group.handler(EVENT, es_client=es_client), read


def failed_components() -> list[str]:
    uri = EVENT.failed_components_s3_uri(EVENT.group)
    return df_from_s3_parquet(uri)["component_id"].to_list()


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
    result, read = run(monkeypatch)

    assert read == {w.state.canonical_id for w in (sierra, miro, lone)}
    assert (result.component_count, result.work_count, result.image_count) == (2, 3, 1)
    assert (result.superseded_count, result.failed_component_count) == (0, 0)
    assert failed_components() == []


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
        version_from_datetime(SNAPSHOT_TIME)
    }
    assert {op["_version_type"] for op in MockElasticsearchClient.inputs} == {
        "external_gte"
    }


@pytest.mark.parametrize(
    "documents",
    [
        {i: w.model_dump_json() for i, w in WORKS.items() if w != miro},
        {
            **{i: w.model_dump_json() for i, w in WORKS.items()},
            miro.state.canonical_id: "{",
        },
    ],
    ids=["a work missing from the table", "a document that does not parse"],
)
def test_a_component_that_cannot_be_read_fails_without_stopping_the_group(
    monkeypatch: pytest.MonkeyPatch, documents: dict[str, str]
) -> None:
    result, _ = run(monkeypatch, documents=documents)

    assert set(written("works-denormalised-python-2026-01-01")) == {
        lone.state.canonical_id
    }
    assert result.failed_component_count == 1
    assert failed_components() == ["c1"]


def test_a_component_that_fails_to_merge_does_not_stop_the_group(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def merge_component(works: list[IdentifiedWork]) -> MergedComponent:
        if lone in works:
            raise RuntimeError("a rule failed")
        return original(works)

    original = merge_group.merge_component
    monkeypatch.setattr(merge_group, "merge_component", merge_component)

    result, _ = run(monkeypatch)

    assert lone.state.canonical_id not in written(
        "works-denormalised-python-2026-01-01"
    )
    assert result.work_count == 2
    assert failed_components() == ["c2"]


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

    assert (result.work_count, result.superseded_count) == (2, 1)


def test_a_document_elasticsearch_refuses_fails_its_component(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockElasticsearchClient.bulk_errors = [
        {
            "index": {
                "_id": lone.state.canonical_id,
                "error": {"type": "mapper_parsing_exception"},
            }
        }
    ]

    result, _ = run(monkeypatch)

    assert (result.work_count, result.failed_component_count) == (2, 1)
    assert failed_components() == ["c2"]


def test_a_group_whose_snapshot_has_expired_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    table = iceberg_table(pl.DataFrame(), EVENT.iceberg_snapshot_id)
    table.snapshot_by_id.return_value = None

    with pytest.raises(ValueError, match="Snapshot 7 not found"):
        run(monkeypatch, table=table)
