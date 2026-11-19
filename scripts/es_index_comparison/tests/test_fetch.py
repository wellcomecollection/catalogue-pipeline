from __future__ import annotations

from es_index_comparison import fetch
from es_index_comparison.buckets import read_bucket


def test_fetch_index_writes_the_id_and_source_of_every_hit(tmp_path, monkeypatch):
    hits = [{"_id": f"doc-{i}", "_index": "works", "_source": {"n": i}} for i in range(10)]
    queries = []

    def fake_scan(es, index, query, scroll):
        queries.append(query)
        return iter(hits)

    monkeypatch.setattr(fetch.helpers, "scan", fake_scan)

    count = fetch.fetch_index(None, "works", tmp_path, None, bucket_count=2)

    assert count == 10
    assert queries == [fetch.MATCH_ALL]
    assert read_bucket(tmp_path, 0) | read_bucket(tmp_path, 1) == {
        hit["_id"]: hit["_source"] for hit in hits
    }
