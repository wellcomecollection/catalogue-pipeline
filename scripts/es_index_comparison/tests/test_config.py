from __future__ import annotations

import pytest
import yaml

from es_index_comparison.analysis import load_normaliser
from es_index_comparison import config
from es_index_comparison.config import load_config


def _write_config(tmp_path, raw):
    path = tmp_path / "analysis.yaml"
    path.write_text(yaml.safe_dump(raw))
    return path


BASE = {"index_sources": ["side-a", "side-b"]}


def test_shared_filter_query_applies_to_both_sides(tmp_path):
    body = {"query": {"term": {"field": "value"}}}
    cfg = load_config(_write_config(tmp_path, {**BASE, "filter_query": body}))

    assert cfg.fetch_queries("side-a") == [body]
    assert cfg.fetch_queries("side-b") == [body]


def test_per_source_filter_overrides_shared(tmp_path):
    shared = {"query": {"term": {"field": "shared"}}}
    per_side = {"query": {"term": {"field": "a-only"}}}
    cfg = load_config(
        _write_config(
            tmp_path,
            {**BASE, "filter_query": shared, "filter_queries": {"side-a": per_side}},
        )
    )

    assert cfg.fetch_queries("side-a") == [per_side]
    assert cfg.fetch_queries("side-b") == [shared]


def test_filter_queries_key_must_match_an_index_source(tmp_path):
    config = _write_config(
        tmp_path, {**BASE, "filter_queries": {"side-c": {"query": {"match_all": {}}}}}
    )

    with pytest.raises(ValueError, match="side-c"):
        load_config(config)


def test_ids_file_builds_ids_query(tmp_path):
    ids = tmp_path / "ids.txt"
    ids.write_text("one\n\n# comment\ntwo\n")
    cfg = load_config(_write_config(tmp_path, {**BASE, "ids_file": "ids.txt"}))

    assert cfg.fetch_queries("side-a") == [{"query": {"ids": {"values": ["one", "two"]}}}]


def test_ids_format_wraps_each_line(tmp_path):
    ids = tmp_path / "ids.txt"
    ids.write_text("abc\n")
    cfg = load_config(
        _write_config(
            tmp_path, {**BASE, "ids_file": "ids.txt", "ids_format": "Work[calm-record-id/{}]"}
        )
    )

    assert cfg.fetch_queries("side-a") == [
        {"query": {"ids": {"values": ["Work[calm-record-id/abc]"]}}}
    ]


def test_ids_file_combines_with_filter_as_bool(tmp_path):
    ids = tmp_path / "ids.txt"
    ids.write_text("one\n")
    body = {"query": {"term": {"field": "value"}}}
    cfg = load_config(
        _write_config(tmp_path, {**BASE, "ids_file": "ids.txt", "filter_query": body})
    )

    assert cfg.fetch_queries("side-a") == [
        {
            "query": {
                "bool": {
                    "filter": [
                        {"ids": {"values": ["one"]}},
                        {"term": {"field": "value"}},
                    ]
                }
            }
        }
    ]


def test_ids_file_resolves_relative_to_config_dir(tmp_path):
    nested = tmp_path / "configs"
    nested.mkdir()
    (tmp_path / "ids.txt").write_text("one\n")
    config = nested / "analysis.yaml"
    config.write_text(yaml.safe_dump({**BASE, "ids_file": "../ids.txt"}))

    cfg = load_config(config)

    assert cfg.fetch_queries("side-a")


def test_missing_ids_file_fails_at_load(tmp_path):
    config = _write_config(tmp_path, {**BASE, "ids_file": "absent.txt"})

    with pytest.raises(ValueError, match="ids_file"):
        load_config(config)


def test_ids_format_without_ids_file_fails(tmp_path):
    config = _write_config(tmp_path, {**BASE, "ids_format": "Work[{}]"})

    with pytest.raises(ValueError, match="ids_format requires ids_file"):
        load_config(config)


def test_normaliser_resolves_relative_to_config_dir(tmp_path):
    (tmp_path / "rules.py").write_text("def normalise(a, b):\n    return {**a, 'seen': True}, b\n")

    cfg = load_config(_write_config(tmp_path, {**BASE, "normaliser": "rules.py"}))
    normalise = load_normaliser(cfg.normaliser)

    assert normalise({"x": 1}, {"x": 2}) == ({"x": 1, "seen": True}, {"x": 2})


def test_no_normaliser_by_default(tmp_path):
    assert load_config(_write_config(tmp_path, BASE)).normaliser is None


def test_missing_normaliser_fails_at_load(tmp_path):
    config = _write_config(tmp_path, {**BASE, "normaliser": "missing.py"})

    with pytest.raises(ValueError, match="normaliser"):
        load_config(config)


def test_wrong_types_fail_at_load(tmp_path):
    for bad in ({"sample_size": "ten"}, {"ignore_fields": "version"}, {"workers": 0}):
        with pytest.raises(ValueError, match=next(iter(bad))):
            load_config(_write_config(tmp_path, {**BASE, **bad}))


def test_index_sources_must_be_exactly_two(tmp_path):
    with pytest.raises(ValueError, match="index_sources"):
        load_config(_write_config(tmp_path, {"index_sources": ["side-a"]}))


def test_key_with_no_value_takes_its_default(tmp_path):
    cfg = load_config(_write_config(tmp_path, {**BASE, "ignore_fields": None}))

    assert cfg.ignore_fields == []


def test_namespace_defaults_to_the_config_file_name(tmp_path):
    assert load_config(_write_config(tmp_path, BASE)).namespace == "analysis"
    assert load_config(_write_config(tmp_path, {**BASE, "namespace": "x"})).namespace == "x"


def test_no_filter_fetches_everything(tmp_path):
    cfg = load_config(_write_config(tmp_path, BASE))

    assert cfg.fetch_queries("side-a") == [{"query": {"match_all": {}}}]


def test_long_ids_file_is_split_into_batches(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "IDS_BATCH_SIZE", 2)
    (tmp_path / "ids.txt").write_text("a\nb\nc\nd\ne\n")
    cfg = load_config(_write_config(tmp_path, {**BASE, "ids_file": "ids.txt"}))

    assert cfg.fetch_queries("side-a") == [
        {"query": {"ids": {"values": ["a", "b"]}}},
        {"query": {"ids": {"values": ["c", "d"]}}},
        {"query": {"ids": {"values": ["e"]}}},
    ]
