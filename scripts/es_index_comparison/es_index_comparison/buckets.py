"""The documents of one index on disk, as gzipped JSON lines split into hash buckets.

A document always lands in the same bucket for a given bucket count, so two
indices can be compared one bucket at a time.
"""

from __future__ import annotations

import gzip
import hashlib
import json
from contextlib import ExitStack
from pathlib import Path


def bucket_for_id(doc_id: str, bucket_count: int) -> int:
    digest = hashlib.blake2s(str(doc_id).encode(), digest_size=8).digest()
    return int.from_bytes(digest, "big") % bucket_count


def bucket_files(folder: Path) -> list[Path]:
    return sorted(folder.glob("bucket_*.ndjson.gz"))


def read_bucket(folder: Path, bucket_id: int) -> dict[str, dict]:
    """Return the documents of one bucket as a mapping of _id to _source."""
    with gzip.open(bucket_files(folder)[bucket_id], "rt") as lines:
        return {doc["_id"]: doc["_source"] for doc in map(json.loads, lines)}


class BucketWriter:
    """Writes documents into one file per bucket, replacing any earlier files."""

    def __init__(self, folder: Path, bucket_count: int):
        folder.mkdir(parents=True, exist_ok=True)
        for old_file in bucket_files(folder):
            old_file.unlink()
        self._stack = ExitStack()
        self._files = [
            self._stack.enter_context(
                gzip.open(folder / f"bucket_{bucket_id:05d}.ndjson.gz", "wt", compresslevel=6)
            )
            for bucket_id in range(bucket_count)
        ]

    def write(self, doc_id: str, source: dict) -> None:
        line = json.dumps({"_id": doc_id, "_source": source}, ensure_ascii=False)
        self._files[bucket_for_id(doc_id, len(self._files))].write(line + "\n")

    def __enter__(self) -> BucketWriter:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self._stack.close()
