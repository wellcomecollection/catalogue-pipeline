from __future__ import annotations

from contextlib import nullcontext

import pytest

from es_index_comparison import config, fetch
from es_index_comparison.buckets import BucketWriter, read_bucket
from es_index_comparison.source_config import Cluster, ResolvedIndexSource

CLUSTER = Cluster.model_validate({"api_key": "key", "host": "https://example.test:443"})
SOURCE_A, SOURCE_B = [
    ResolvedIndexSource(name, name, "cluster", CLUSTER) for name in ("source-a", "source-b")
]
QUERIES = {name: [config.MATCH_ALL] for name in ("source-a", "source-b")}


def read_all(folder) -> dict[str, dict]:
    return read_bucket(folder, 0) | read_bucket(folder, 1)


def test_fetch_index_writes_the_id_and_source_of_every_hit_of_every_query(tmp_path, monkeypatch):
    hits = [{"_id": f"doc-{i}", "_index": "works", "_source": {"n": i}} for i in range(10)]
    queries = [{"query": {"ids": {"values": ["first half"]}}}, {"query": {"match_all": {}}}]
    pages = {id(queries[0]): hits[:5], id(queries[1]): hits[5:]}
    monkeypatch.setattr(fetch.helpers, "scan", lambda es, index, query, scroll: pages[id(query)])

    with BucketWriter(tmp_path, 2) as writer:
        count = fetch.fetch_index(None, "works", writer, queries)

    assert count == 10
    assert read_all(tmp_path) == {hit["_id"]: hit["_source"] for hit in hits}


def test_fetch_both_keeps_the_earlier_files_of_every_source_when_one_fails(tmp_path, monkeypatch):
    monkeypatch.setattr(Cluster, "connect", lambda self: nullcontext())
    version = {"n": 1}

    def scan(es, index, query, scroll):
        yield {"_id": "doc-1", "_source": {"index": index, "version": version["n"]}}
        if version["n"] == 2 and index == "source-b":
            raise RuntimeError("fetch failed")

    monkeypatch.setattr(fetch.helpers, "scan", scan)
    fetch.fetch_both([SOURCE_A, SOURCE_B], tmp_path, QUERIES, bucket_count=2)

    version["n"] = 2
    with pytest.raises(RuntimeError):
        fetch.fetch_both([SOURCE_A, SOURCE_B], tmp_path, QUERIES, bucket_count=2)

    assert read_all(tmp_path / "source-a") == {"doc-1": {"index": "source-a", "version": 1}}
    assert read_all(tmp_path / "source-b") == {"doc-1": {"index": "source-b", "version": 1}}
    assert sorted(path.name for path in tmp_path.iterdir()) == ["source-a", "source-b"]
