from __future__ import annotations

from es_index_comparison import fetch
from es_index_comparison.buckets import read_bucket


def test_fetch_index_writes_the_id_and_source_of_every_hit_of_every_query(tmp_path, monkeypatch):
    hits = [{"_id": f"doc-{i}", "_index": "works", "_source": {"n": i}} for i in range(10)]
    queries = [{"query": {"ids": {"values": ["first half"]}}}, {"query": {"match_all": {}}}]
    pages = {id(queries[0]): hits[:5], id(queries[1]): hits[5:]}
    monkeypatch.setattr(fetch.helpers, "scan", lambda es, index, query, scroll: pages[id(query)])

    count = fetch.fetch_index(None, "works", tmp_path, queries, bucket_count=2)

    assert count == 10
    assert read_bucket(tmp_path, 0) | read_bucket(tmp_path, 1) == {
        hit["_id"]: hit["_source"] for hit in hits
    }
