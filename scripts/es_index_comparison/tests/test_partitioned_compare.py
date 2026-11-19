from __future__ import annotations

import gzip
import json
from collections import Counter
from pathlib import Path

import polars as pl

from es_index_comparison.parquet_io import (
    MANIFEST_FILENAME,
    PartitionedIndex,
    _hash_bucket_for_id,
    ndjson_gz_to_parquet_shards,
)
from es_index_comparison.analysis import compare_indices, field_name
from es_index_comparison.source_config import ResolvedIndexSource


def _write_docs(path: Path, docs: list[dict]) -> None:
    with gzip.open(path, "wt") as fh:
        for doc in docs:
            fh.write(json.dumps(doc) + "\n")


def _hit(doc_id: str, payload: dict[str, object]) -> dict:
    return {
        "_id": doc_id,
        "_source": payload,
        "_index": "test-index",
        "_version": 1,
        "_seq_no": 1,
    }


def test_manifest_records_hash_buckets(tmp_path):
    docs = [_hit(f"doc-{i}", {"value": i}) for i in range(6)]
    raw_path = tmp_path / "raw.ndjson.gz"
    parquet_root = tmp_path / "parquet"
    _write_docs(raw_path, docs)

    ndjson_gz_to_parquet_shards(
        index="sample-index",
        ndjson_path=raw_path,
        out_parent=parquet_root,
        chunk_size=2,
        hash_bucket_count=4,
    )

    manifest_path = parquet_root / "sample-index" / MANIFEST_FILENAME
    assert manifest_path.exists(), "manifest.json should be created alongside shards"
    manifest = json.loads(manifest_path.read_text())
    assert manifest["hash_bucket_count"] == 4
    assert manifest["total_docs"] == len(docs)

    reader = PartitionedIndex(parquet_root / "sample-index")
    bucket_counts = {
        bucket: reader.bucket_doc_count(bucket) for bucket in range(reader.hash_bucket_count)
    }

    expected = Counter(_hash_bucket_for_id(doc["_id"], 4) for doc in docs)
    for bucket_id, expected_count in expected.items():
        assert bucket_counts[bucket_id] == expected_count
    assert sum(bucket_counts.values()) == len(docs)


def test_convert_flushes_global_chunks(tmp_path, monkeypatch):
    docs = [_hit(f"doc-{i}", {"value": i}) for i in range(5)]
    raw_path = tmp_path / "raw.ndjson.gz"
    parquet_root = tmp_path / "parquet"
    _write_docs(raw_path, docs)

    monkeypatch.setattr(
        "es_index_comparison.parquet_io._hash_bucket_for_id", lambda _doc_id, _count: 0
    )

    ndjson_gz_to_parquet_shards(
        index="sample-index",
        ndjson_path=raw_path,
        out_parent=parquet_root,
        chunk_size=2,
        hash_bucket_count=4,
    )

    bucket_dir = parquet_root / "sample-index" / "bucket_00000"
    parts = sorted(bucket_dir.glob("part-*.parquet"))
    assert len(parts) == 3  # 2 + 2 + 1 rows flushed sequentially
    row_counts = [pl.read_parquet(part).height for part in parts]
    assert row_counts == [2, 2, 1]


def test_compare_indices_streams_partitions(tmp_path):
    parquet_root = tmp_path / "parquet"
    diff_dir = tmp_path / "diffs"
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()

    docs_a = [
        _hit("doc-1", {"title": "Original"}),
        _hit("doc-2", {"title": "OnlyInA"}),
    ]
    docs_b = [
        _hit("doc-1", {"title": "Changed"}),
        _hit("doc-3", {"title": "OnlyInB"}),
    ]

    raw_a = raw_dir / "a.ndjson.gz"
    raw_b = raw_dir / "b.ndjson.gz"
    _write_docs(raw_a, docs_a)
    _write_docs(raw_b, docs_b)

    ndjson_gz_to_parquet_shards("source-a", raw_a, parquet_root, chunk_size=1, hash_bucket_count=8)
    ndjson_gz_to_parquet_shards("source-b", raw_b, parquet_root, chunk_size=1, hash_bucket_count=8)

    source_a = ResolvedIndexSource(
        id="source-a",
        index="index-a",
        cluster_id="cluster",
        cloud_id="cloud",
        api_key="key",
    )
    source_b = ResolvedIndexSource(
        id="source-b",
        index="index-b",
        cluster_id="cluster",
        cloud_id="cloud",
        api_key="key",
    )

    result = compare_indices(
        source_a,
        source_b,
        parquet_root=parquet_root,
        ignore_fields=[],
        base_diff_folder=diff_dir,
        sample_size=2,
    )

    assert result["only_in_a"] == ["doc-2"]
    assert result["only_in_b"] == ["doc-3"]
    assert result["differing"] == 1

    diffs_jsonl = (diff_dir / "diffs.jsonl").read_text().strip().splitlines()
    assert len(diffs_jsonl) == 1
    diff_record = json.loads(diffs_jsonl[0])
    assert diff_record["_id"] == "doc-1"
    assert diff_record["diffs"], "diff payload should be present"
    assert any(d["path"].startswith("title") for d in diff_record["diffs"])

    summary = json.loads((diff_dir / "summary.json").read_text())
    assert summary == result
    assert summary["compared"] == 1
    assert json.loads((diff_dir / "field_frequency.json").read_text()) == {"title": 1}


def test_convert_respects_bucket_filter(tmp_path, monkeypatch):
    docs = [_hit("keep", {"value": 1}), _hit("drop", {"value": 2})]
    raw_path = tmp_path / "raw.ndjson.gz"
    parquet_root = tmp_path / "parquet"
    _write_docs(raw_path, docs)

    def fake_hash(doc_id: str, bucket_count: int) -> int:
        if doc_id == "keep":
            return 1
        if doc_id == "drop":
            return 2
        return 0

    monkeypatch.setattr("es_index_comparison.parquet_io._hash_bucket_for_id", fake_hash)

    ndjson_gz_to_parquet_shards(
        index="sample-index",
        ndjson_path=raw_path,
        out_parent=parquet_root,
        chunk_size=1,
        hash_bucket_count=4,
        bucket_filter={1},
    )

    index_path = parquet_root / "sample-index"
    bucket_dirs = sorted(index_path.glob("bucket_*"))
    assert len(bucket_dirs) == 1
    assert bucket_dirs[0].name.endswith("00001"), "only the selected bucket should be written"

    reader = PartitionedIndex(index_path)
    assert reader.total_docs == 1
    assert reader.bucket_doc_count(1) == 1


def test_compare_indices_respects_bucket_filter(tmp_path, monkeypatch):
    parquet_root = tmp_path / "parquet"
    diff_dir = tmp_path / "diffs"
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()

    docs_a = [
        _hit("doc-1", {"title": "Original"}),
        _hit("doc-2", {"title": "OnlyInA"}),
    ]
    docs_b = [
        _hit("doc-1", {"title": "Changed"}),
        _hit("doc-3", {"title": "OnlyInB"}),
    ]

    raw_a = raw_dir / "a.ndjson.gz"
    raw_b = raw_dir / "b.ndjson.gz"
    _write_docs(raw_a, docs_a)
    _write_docs(raw_b, docs_b)

    def fake_hash(doc_id: str, bucket_count: int) -> int:
        return int(doc_id.split("-")[-1]) % bucket_count

    monkeypatch.setattr("es_index_comparison.parquet_io._hash_bucket_for_id", fake_hash)

    ndjson_gz_to_parquet_shards("source-a", raw_a, parquet_root, chunk_size=1, hash_bucket_count=8)
    ndjson_gz_to_parquet_shards("source-b", raw_b, parquet_root, chunk_size=1, hash_bucket_count=8)

    source_a = ResolvedIndexSource(
        id="source-a",
        index="index-a",
        cluster_id="cluster",
        cloud_id="cloud",
        api_key="key",
    )
    source_b = ResolvedIndexSource(
        id="source-b",
        index="index-b",
        cluster_id="cluster",
        cloud_id="cloud",
        api_key="key",
    )

    bucket_filter = {1}
    result = compare_indices(
        source_a,
        source_b,
        parquet_root=parquet_root,
        ignore_fields=[],
        base_diff_folder=diff_dir,
        sample_size=2,
        bucket_filter=bucket_filter,
    )

    assert result["differing"] == 1
    assert result["only_in_a"] == []
    assert result["only_in_b"] == []

    diffs_jsonl = (diff_dir / "diffs.jsonl").read_text().strip().splitlines()
    assert len(diffs_jsonl) == 1
    diff_record = json.loads(diffs_jsonl[0])
    assert diff_record["_id"] == "doc-1"


def test_compare_indices_applies_normaliser(tmp_path):
    parquet_root = tmp_path / "parquet"
    for name, title in (("source-a", "Title "), ("source-b", "Title")):
        raw = tmp_path / f"{name}.ndjson.gz"
        _write_docs(raw, [_hit("doc-1", {"title": title})])
        ndjson_gz_to_parquet_shards(name, raw, parquet_root, chunk_size=1, hash_bucket_count=2)
    source_a, source_b = [
        ResolvedIndexSource(id=name, index=name, cluster_id="cluster", api_key="key")
        for name in ("source-a", "source-b")
    ]

    strip_titles = tmp_path / "strip_titles.py"
    strip_titles.write_text(
        "def normalise(a, b):\n"
        "    return {'title': a['title'].strip()}, {'title': b['title'].strip()}\n"
    )
    skip_all = tmp_path / "skip_all.py"
    skip_all.write_text("def normalise(a, b):\n    return None\n")

    def compare(name, normaliser=None):
        return compare_indices(
            source_a, source_b, parquet_root, [], tmp_path / name, 1, normaliser=normaliser
        )

    assert compare("plain")["differing"] == 1
    assert compare("normalised", strip_titles)["differing"] == 0
    skipped = compare("skipped", skip_all)
    assert (skipped["skipped"], skipped["compared"], skipped["differing"]) == (1, 0, 0)


def test_compare_indices_gives_the_same_result_with_several_workers(tmp_path):
    parquet_root = tmp_path / "parquet"
    for name, suffix in (("source-a", "a"), ("source-b", "b")):
        raw = tmp_path / f"{name}.ndjson.gz"
        _write_docs(raw, [_hit(f"doc-{i}", {"title": f"{i}{suffix}"}) for i in range(20)])
        ndjson_gz_to_parquet_shards(name, raw, parquet_root, chunk_size=50, hash_bucket_count=4)
    source_a, source_b = [
        ResolvedIndexSource(id=name, index=name, cluster_id="cluster", api_key="key")
        for name in ("source-a", "source-b")
    ]

    one = compare_indices(source_a, source_b, parquet_root, [], tmp_path / "one", 1, workers=1)
    three = compare_indices(source_a, source_b, parquet_root, [], tmp_path / "three", 1, workers=3)

    assert one == three
    assert one["differing"] == 20
    assert (tmp_path / "one/diffs.jsonl").read_text() == (
        tmp_path / "three/diffs.jsonl"
    ).read_text()


def test_field_name_keeps_the_first_segments_without_list_indexes():
    assert field_name("data.production[0].dates[1].label", 2) == "data.production"
    assert field_name("data.production[0].dates[1].label", 3) == "data.production.dates"
    assert field_name("type", 2) == "type"
