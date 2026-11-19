from __future__ import annotations

import json

import pytest

from es_index_comparison.analysis import compare_indices, field_name
from es_index_comparison.buckets import BucketWriter, bucket_for_id
from es_index_comparison.config import AppConfig
from es_index_comparison.source_config import Cluster, ResolvedIndexSource

CLUSTER = Cluster.model_validate({"api_key": "key", "host": "https://example.test:443"})
SOURCE_A, SOURCE_B = [
    ResolvedIndexSource(name, name, "cluster", CLUSTER) for name in ("source-a", "source-b")
]
BUCKET_COUNT = 4


def write_index(tmp_path, source, docs: dict[str, dict], bucket_count: int = BUCKET_COUNT) -> None:
    with BucketWriter(tmp_path / "docs" / source.storage_key, bucket_count) as writer:
        for doc_id, body in docs.items():
            writer.write(doc_id, body)


def compare(tmp_path, name: str = "diffs", bucket_filter=None, **settings) -> dict:
    cfg = AppConfig(index_sources=["source-a", "source-b"], namespace="test", **settings)
    return compare_indices(
        cfg, SOURCE_A, SOURCE_B, tmp_path / "docs", tmp_path / name, bucket_filter=bucket_filter
    )


def test_compare_reports_differences_and_membership(tmp_path):
    write_index(tmp_path, SOURCE_A, {"doc-1": {"title": "Original"}, "doc-2": {"title": "A"}})
    write_index(tmp_path, SOURCE_B, {"doc-1": {"title": "Changed"}, "doc-3": {"title": "B"}})

    result = compare(tmp_path)

    assert result["only_in_a"] == ["doc-2"]
    assert result["only_in_b"] == ["doc-3"]
    assert (result["compared"], result["differing"]) == (1, 1)
    diffs = [json.loads(line) for line in (tmp_path / "diffs/diffs.jsonl").read_text().splitlines()]
    assert diffs == [
        {
            "_id": "doc-1",
            "diffs": [
                {"path": "title", "type": "values_changed", "left": "Original", "right": "Changed"}
            ],
        }
    ]
    assert json.loads((tmp_path / "diffs/summary.json").read_text()) == result
    assert json.loads((tmp_path / "diffs/field_frequency.json").read_text()) == {"title": 1}


def test_compare_respects_bucket_filter(tmp_path):
    docs = {f"doc-{i}": {"title": str(i)} for i in range(20)}
    write_index(tmp_path, SOURCE_A, docs)
    write_index(tmp_path, SOURCE_B, {doc_id: {"title": "other"} for doc_id in docs})
    in_bucket_1 = sum(bucket_for_id(doc_id, BUCKET_COUNT) == 1 for doc_id in docs)

    result = compare(tmp_path, bucket_filter={1})

    assert 0 < in_bucket_1 < len(docs)
    assert result["differing"] == in_bucket_1


def test_compare_applies_normaliser(tmp_path):
    write_index(tmp_path, SOURCE_A, {"doc-1": {"title": "Title "}})
    write_index(tmp_path, SOURCE_B, {"doc-1": {"title": "Title"}})
    strip_titles = tmp_path / "strip_titles.py"
    strip_titles.write_text(
        "def normalise(a, b):\n"
        "    return {'title': a['title'].strip()}, {'title': b['title'].strip()}\n"
    )
    skip_all = tmp_path / "skip_all.py"
    skip_all.write_text("def normalise(a, b):\n    return None\n")

    assert compare(tmp_path, "plain")["differing"] == 1
    assert compare(tmp_path, "normalised", normaliser=strip_titles)["differing"] == 0
    skipped = compare(tmp_path, "skipped", normaliser=skip_all)
    assert (skipped["skipped"], skipped["compared"], skipped["differing"]) == (1, 0, 0)


def test_compare_gives_the_same_result_with_several_workers(tmp_path):
    write_index(tmp_path, SOURCE_A, {f"doc-{i}": {"title": f"{i}a"} for i in range(20)})
    write_index(tmp_path, SOURCE_B, {f"doc-{i}": {"title": f"{i}b"} for i in range(20)})

    one = compare(tmp_path, "one", workers=1)
    three = compare(tmp_path, "three", workers=3)

    assert one == three
    assert one["differing"] == 20
    assert (tmp_path / "one/diffs.jsonl").read_text() == (
        tmp_path / "three/diffs.jsonl"
    ).read_text()


def test_compare_needs_the_same_bucket_count_on_both_sides(tmp_path):
    write_index(tmp_path, SOURCE_A, {"doc-1": {}}, bucket_count=2)
    write_index(tmp_path, SOURCE_B, {"doc-1": {}}, bucket_count=3)

    with pytest.raises(ValueError, match="same number of bucket files"):
        compare(tmp_path)


def test_field_name_keeps_the_first_segments_without_list_indexes():
    assert field_name("data.production[0].dates[1].label", 2) == "data.production"
    assert field_name("data.production[0].dates[1].label", 3) == "data.production.dates"
    assert field_name("type", 2) == "type"
