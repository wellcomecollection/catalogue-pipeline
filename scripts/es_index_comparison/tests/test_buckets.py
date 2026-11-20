from __future__ import annotations

import pytest

from es_index_comparison.buckets import BucketWriter, bucket_files, bucket_for_id, read_bucket


def test_documents_are_read_back_from_the_bucket_of_their_id(tmp_path):
    docs = {f"doc-{i}": {"title": f"Titel {i} é", "tags": [i]} for i in range(30)}

    with BucketWriter(tmp_path, 4) as writer:
        for doc_id, source in docs.items():
            writer.write(doc_id, source)

    assert len(bucket_files(tmp_path)) == 4
    for bucket_id in range(4):
        expected = {
            doc_id: source
            for doc_id, source in docs.items()
            if bucket_for_id(doc_id, 4) == bucket_id
        }
        assert read_bucket(tmp_path, bucket_id) == expected


def test_writing_again_replaces_earlier_bucket_files(tmp_path):
    with BucketWriter(tmp_path, 8) as writer:
        writer.write("old", {})
    with BucketWriter(tmp_path, 2) as writer:
        writer.write("new", {})

    assert len(bucket_files(tmp_path)) == 2
    assert read_bucket(tmp_path, 0) | read_bucket(tmp_path, 1) == {"new": {}}


def test_a_failed_write_leaves_the_earlier_bucket_files(tmp_path):
    folder = tmp_path / "index"
    with BucketWriter(folder, 2) as writer:
        writer.write("old", {})

    with pytest.raises(RuntimeError), BucketWriter(folder, 2) as writer:
        writer.write("new", {})
        raise RuntimeError("fetch failed")

    assert read_bucket(folder, 0) | read_bucket(folder, 1) == {"old": {}}
    assert list(tmp_path.iterdir()) == [folder]
