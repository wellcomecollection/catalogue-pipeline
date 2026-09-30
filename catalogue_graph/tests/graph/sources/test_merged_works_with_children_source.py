import re
from unittest.mock import MagicMock

import pytest

from graph.sources.merged_works_source import MergedWorksSource
from graph.sources.merged_works_with_children_source import (
    COLLECTION_PATH_KEYWORD_FIELD,
    MergedWorksWithChildrenSource,
    child_path_prefixes,
)
from models.events import BasePipelineEvent


def _make_work(
    canonical_id: str,
    path: str | None = None,
    source_identifier: str = "some-source-id",
    other_identifiers: list[str] | None = None,
) -> dict:
    work: dict = {
        "state": {
            "canonicalId": canonical_id,
            "sourceIdentifier": {"value": source_identifier},
        },
        "data": {"otherIdentifiers": [{"value": i} for i in other_identifiers or []]},
    }
    if path is not None:
        work["data"]["collectionPath"] = {"path": path}
    return work


def _child_regexps(path_prefixes: set[str]) -> list[str]:
    child_source = _make_source()._get_child_source(path_prefixes)
    clauses = child_source.query["bool"]["must"][1]["bool"]["should"]
    return sorted(c["regexp"][COLLECTION_PATH_KEYWORD_FIELD] for c in clauses)


def _make_source(query: dict | None = None) -> MergedWorksWithChildrenSource:
    event = BasePipelineEvent(pipeline_date="dev", graph_date="dev")
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
    mock_get_child.assert_called_once_with({"A/B", "B"})


def test_stream_raw_batches_prefixes_by_clause_limit(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Partial-path works contribute two prefixes each, so batching must count prefixes, not works
    works = [_make_work("w1", path="A/B"), _make_work("w2", path="C/D")]
    _with_primary_works(monkeypatch, works)
    monkeypatch.setattr(
        "graph.sources.merged_works_with_children_source.MAX_BOOL_CLAUSES", 3
    )

    source = _make_source()
    mock_get_child = MagicMock(return_value=MagicMock(stream_raw=lambda: iter([])))
    monkeypatch.setattr(
        MergedWorksWithChildrenSource, "_get_child_source", mock_get_child
    )

    list(source.stream_raw())
    batches = [call.args[0] for call in mock_get_child.call_args_list]
    assert [len(b) for b in batches] == [3, 1]
    assert set().union(*batches) == {"A/B", "B", "C/D", "D"}


def test_prefixes_for_full_path_work_are_deduplicated() -> None:
    # Axiell in RefNo mode: the path is the work's own RefNo, so it is also its path identifier
    work = _make_work("w1", path="PP/ABC/1", other_identifiers=["PP/ABC/1"])
    assert child_path_prefixes(work) == {"PP/ABC/1"}
    assert _child_regexps(child_path_prefixes(work)) == ['"pp/abc/1"/[^/]+']


def test_prefixes_for_part_of_mode_work_include_path_identifier() -> None:
    work = _make_work(
        "w1", path="axiell:PP|ABC/axiell:PP|ABC|1", other_identifiers=["PP/ABC/1"]
    )
    assert child_path_prefixes(work) == {
        "axiell:PP|ABC/axiell:PP|ABC|1",
        "axiell:PP|ABC|1",
    }


def test_prefixes_for_part_of_mode_root_are_deduplicated() -> None:
    work = _make_work("w1", path="axiell:PP|ABC", other_identifiers=["PP/ABC"])
    assert child_path_prefixes(work) == {"axiell:PP|ABC"}


def test_prefixes_for_partial_path_work_include_full_path() -> None:
    # TEI: the path lists ancestors' ids, and children extend the full path
    work = _make_work(
        "w1", path="MS_123/MS_123_item_1", source_identifier="MS_123_item_1"
    )
    assert child_path_prefixes(work) == {"MS_123/MS_123_item_1", "MS_123_item_1"}


def test_prefixes_strip_trailing_slash() -> None:
    work = _make_work("w1", path="PP/ABC/1/", other_identifiers=["PP/ABC/1/"])
    assert child_path_prefixes(work) == {"PP/ABC/1"}


def test_prefixes_empty_without_path() -> None:
    assert child_path_prefixes(_make_work("w1")) == set()
    assert child_path_prefixes(_make_work("w1", path="")) == set()


def test_child_query_quotes_special_characters() -> None:
    assert _child_regexps({"axiell:PP|A&B 1.2(3)*+?[x]{y}~<z>#@"}) == [
        '"axiell:pp|a&b 1.2(3)*+?[x]{y}~<z>#@"/[^/]+'
    ]


def test_child_query_escapes_double_quote_outside_quotes() -> None:
    assert _child_regexps({'A "B" \\ C'}) == ['"a "\\""b"\\"" \\ c"/[^/]+']


@pytest.mark.parametrize(
    "parent_path,other_identifiers,child_path,expected",
    [
        # Full-path (RefNo mode) children extend the parent's path
        ("PP/ABC/1", ["PP/ABC/1"], "PP/ABC/1/2", True),
        ("PP/ABC/1", ["PP/ABC/1"], "PP/ABC/1/2/3", False),
        ("PP/ABC/1", ["PP/ABC/1"], "PP/ABC/12", False),
        # Pointer-mode children are "<parent key>/<child key>"
        (
            "axiell:PP|ABC/axiell:PP|ABC|1",
            ["PP/ABC/1"],
            "axiell:PP|ABC|1/axiell:PP|ABC|1|2",
            True,
        ),
        ("axiell:PP|ABC", ["PP/ABC"], "axiell:PP|ABC/axiell:PP|ABC|1", True),
        ("axiell:PP|ABC", ["PP/ABC"], "axiell:PPXABC/axiell:PP|ABC|1", False),
        ("axiell:PP.A", ["PP.A"], "axiell:PPXA/axiell:PP.A.1", False),
        # Partial (TEI) children extend the parent's full path
        ("MS_1/MS_1_i1", [], "MS_1/MS_1_i1/MS_1_i1_a", True),
        ("MS_1/MS_1_i1", [], "MS_2/MS_1_i1/MS_1_i1_a", False),
        # Special characters are matched literally and case-insensitively
        ("WA/HMM/A & B.1", ["WA/HMM/A & B.1"], "wa/hmm/a & b.1/x", True),
        ("WA/HMM/A & B.1", ["WA/HMM/A & B.1"], "WA/HMM/A & BX1/x", False),
    ],
)
def test_child_regexps_match_expected_children(
    parent_path: str, other_identifiers: list[str], child_path: str, expected: bool
) -> None:
    parent = _make_work("p1", path=parent_path, other_identifiers=other_identifiers)
    patterns = _child_regexps(child_path_prefixes(parent))
    # Emulate the lowercase-normalised keyword field and Lucene's quoted literals
    assert any(_matches(p, child_path.lower()) for p in patterns) == expected


def _matches(pattern: str, value: str) -> bool:
    literal, rest = pattern[1:].split('"', 1)
    assert rest == "/[^/]+"
    return re.fullmatch(re.escape(literal) + "/[^/]+", value) is not None
