"""The documents of one index on disk, as gzipped JSON lines split into hash buckets.

A document always lands in the same bucket for a given bucket count, so two
indices can be compared one bucket at a time.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import shutil
from contextlib import ExitStack
from pathlib import Path
from typing import Self


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
    """Writes documents into one file per bucket.

    The files replace the folder's earlier contents when the writer exits
    without an error. After an error the earlier contents are left as they were.
    """

    def __init__(self, folder: Path, bucket_count: int):
        self._folder = folder
        self._unfinished = folder.with_name(f"{folder.name}.unfinished")
        shutil.rmtree(self._unfinished, ignore_errors=True)
        self._unfinished.mkdir(parents=True)
        self._stack = ExitStack()
        self._files = []
        for bucket_id in range(bucket_count):
            path = self._unfinished / f"bucket_{bucket_id:05d}.ndjson.gz"
            # The stack closes the file.
            file = gzip.open(path, "wt", compresslevel=6)  # noqa: SIM115
            self._files.append(self._stack.enter_context(file))

    def write(self, doc_id: str, source: dict) -> None:
        line = json.dumps({"_id": doc_id, "_source": source}, ensure_ascii=False)
        self._files[bucket_for_id(doc_id, len(self._files))].write(line + "\n")

    def __enter__(self) -> Self:
        return self

    def __exit__(self, error_type: object, *error_details: object) -> None:
        self._stack.close()
        if error_type is None:
            shutil.rmtree(self._folder, ignore_errors=True)
            self._unfinished.rename(self._folder)
        else:
            shutil.rmtree(self._unfinished)
