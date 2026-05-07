from unittest.mock import MagicMock

import pytest

from graph.sources.merged_works_source import MergedWorksSource
from graph.sources.merged_works_with_children_source import (
    COLLECTION_PATH_KEYWORD_FIELD,
    MergedWorksWithChildrenSource,
)
from models.events import BasePipelineEvent


def _make_work(canonical_id: str, path: str | None = None) -> dict:
    work: dict = {"state": {"canonicalId": canonical_id}, "data": {}}
    if path is not None:
        work["data"]["collectionPath"] = {"path": path}
    return work


def _make_source(query: dict | None = None) -> MergedWorksWithChildrenSource:
    event = BasePipelineEvent(pipeline_date="dev")
    es_client = MagicMock()
    es_client.open_point_in_time.return_value = {"id": "some_pit_id"}
    return MergedWorksWithChildrenSource(event=event, es_client=es_client, query=query)


def _with_primary_works(monkeypatch: pytest.MonkeyPatch, works: list[dict]) -> None:
    monkeypatch.setattr(MergedWorksSource, "stream_raw", lambda self: iter(works))


def _with_child_works(monkeypatch: pytest.MonkeyPatch, works: list[dict]) -> None:
    child_source = MagicMock(stream_raw=lambda: iter(works))
    monkeypatch.setattr(
        MergedWorksWithChildrenSource,
        "_get_child_source",
        lambda self, collection_paths: child_source,
    )


def test_child_query_quotes_path_with_special_characters() -> None:
    source = _make_source()
    child_source = source._get_child_source({"Well.Jav.8"})

    clauses = child_source.query["bool"]["must"][1]["bool"]["should"]
    assert len(clauses) == 1
    assert clauses[0] == {
        "regexp": {COLLECTION_PATH_KEYWORD_FIELD: '"well.jav.8"/[^/]+'}
    }


def test_child_query_lowercases_path() -> None:
    source = _make_source()
    child_source = source._get_child_source({"PPDAL/E/2"})

    clauses = child_source.query["bool"]["must"][1]["bool"]["should"]
    assert clauses[0] == {
        "regexp": {COLLECTION_PATH_KEYWORD_FIELD: '"ppdal/e/2"/[^/]+'}
    }


def test_child_query_preserves_base_query() -> None:
    base_query = {"bool": {"must": {"match": {"type": "Visible"}}}}
    source = _make_source(query=base_query)
    child_source = source._get_child_source({"PPDAL/E/2"})
    assert child_source.query["bool"]["must"][0] == base_query


def test_child_query_builds_one_clause_per_path() -> None:
    source = _make_source()
    child_source = source._get_child_source({"A", "B"})

    clauses = child_source.query["bool"]["must"][1]["bool"]["should"]
    assert len(clauses) == 2


def test_stream_raw_yields_parent_then_children(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    parent = _make_work("p1", path="A")
    child1 = _make_work("c1", path="A/X")
    child2 = _make_work("c2", path="A/Y")

    source = _make_source()
    _with_primary_works(monkeypatch, [parent])
    _with_child_works(monkeypatch, [child1, child2])

    results = list(source.stream_raw())
    assert [w["state"]["canonicalId"] for w in results] == ["p1", "c1", "c2"]


def test_stream_raw_deduplicates_children(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    work = _make_work("w1", path="A")

    source = _make_source()
    _with_primary_works(monkeypatch, [work])
    _with_child_works(monkeypatch, [work])

    assert len(list(source.stream_raw())) == 1


def test_stream_raw_skips_child_query_when_no_paths(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    work = _make_work("w1", path=None)
    _with_primary_works(monkeypatch, [work])

    source = _make_source()
    mock_get_child = MagicMock()
    monkeypatch.setattr(
        MergedWorksWithChildrenSource, "_get_child_source", mock_get_child
    )

    assert len(list(source.stream_raw())) == 1
    mock_get_child.assert_not_called()


def test_stream_raw_strips_trailing_slash_from_paths(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    work = _make_work("w1", path="A/B/")
    _with_primary_works(monkeypatch, [work])

    source = _make_source()
    mock_get_child = MagicMock(return_value=MagicMock(stream_raw=lambda: iter([])))
    monkeypatch.setattr(
        MergedWorksWithChildrenSource, "_get_child_source", mock_get_child
    )

    list(source.stream_raw())
    mock_get_child.assert_called_once_with({"A/B"})
