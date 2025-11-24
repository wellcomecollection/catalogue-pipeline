from __future__ import annotations

import gzip
import json
from collections import defaultdict
from pathlib import Path
from typing import Dict, Any, List
import hashlib

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
from rich.console import Console

MANIFEST_FILENAME = "manifest.json"

console = Console()


def _hash_bucket_for_id(doc_id: Any, bucket_count: int) -> int:
    if bucket_count <= 0:
        raise ValueError("bucket_count must be > 0")
    data = str(doc_id).encode("utf-8")
    digest = hashlib.blake2s(data, digest_size=8).digest()
    return int.from_bytes(digest, "big") % bucket_count


def ndjson_gz_to_parquet_shards(
    index: str,
    ndjson_path: Path,
    out_parent: Path,
    chunk_size: int,
    hash_bucket_count: int,
    bucket_filter: set[int] | None = None,
) -> list[Path]:
    """Convert a gzipped NDJSON (Elasticsearch hits) to a directory of parquet shards.

    We build a mapping keyed by _id with entire hit document retained to mimic original notebook logic
    where nested access into _source is needed later.
    """
    if bucket_filter:
        invalid = sorted(b for b in bucket_filter if b < 0 or b >= hash_bucket_count)
        if invalid:
            raise ValueError(
                f"Bucket filter contains invalid IDs {invalid}; valid range is 0-{hash_bucket_count - 1}"
            )

    target_dir = out_parent / index
    target_dir.mkdir(parents=True, exist_ok=True)

    buffers: Dict[int, Dict[str, Dict[str, Any]]] = defaultdict(dict)
    active_buckets: set[int] = set()
    bucket_file_counters: Dict[int, int] = defaultdict(int)
    bucket_meta: Dict[int, Dict[str, Any]] = {
        bucket_id: {"doc_count": 0, "files": []} for bucket_id in range(hash_bucket_count)
    }
    written_files: List[Path] = []
    processed = 0
    buffered_docs = 0

    def flush_bucket(bucket_id: int) -> int:
        bucket_docs = buffers.get(bucket_id)
        if not bucket_docs:
            return 0
        df = pd.DataFrame.from_dict(bucket_docs, orient="index")
        table = pa.Table.from_pandas(df)
        rel_dir = Path(f"bucket_{bucket_id:05d}")
        bucket_dir = target_dir / rel_dir
        bucket_dir.mkdir(parents=True, exist_ok=True)
        seq = bucket_file_counters[bucket_id]
        filename = f"part-{seq:05d}.parquet"
        out_path = bucket_dir / filename
        pq.write_table(table, out_path)
        bucket_file_counters[bucket_id] += 1
        bucket_meta[bucket_id]["files"].append(str(rel_dir / filename))
        written_files.append(out_path)
        buffers[bucket_id] = {}
        active_buckets.discard(bucket_id)
        console.log(
            f"[{index}] bucket={bucket_id:04d} wrote {len(df)} docs -> {out_path.relative_to(target_dir)}"
        )
        return len(df)

    def flush_all_buckets():
        nonlocal buffered_docs
        if buffered_docs == 0 or not active_buckets:
            return
        flushed = 0
        for bucket_id in sorted(list(active_buckets)):
            flushed += flush_bucket(bucket_id)
        buffered_docs = max(buffered_docs - flushed, 0)

    with gzip.open(ndjson_path, "rt") as fh:
        for line in fh:
            doc = json.loads(line)
            _id = doc.get("_id")
            if _id is None:
                continue
            bucket_id = _hash_bucket_for_id(_id, hash_bucket_count)
            if bucket_filter is not None and bucket_id not in bucket_filter:
                continue
            buffers[bucket_id][_id] = doc
            active_buckets.add(bucket_id)
            bucket_meta[bucket_id]["doc_count"] += 1
            processed += 1
            buffered_docs += 1

            if buffered_docs >= chunk_size:
                flush_all_buckets()

            if processed % 50_000 == 0:
                console.log(
                    f"[{index}] processed {processed} docs across {hash_bucket_count} hash buckets"
                )

    flush_all_buckets()

    manifest = {
        "version": 1,
        "index": index,
        "hash_bucket_count": hash_bucket_count,
        "loading_chunk_size": chunk_size,
        "total_docs": processed,
        "buckets": [
            {
                "bucket_id": bucket_id,
                "doc_count": bucket_meta[bucket_id]["doc_count"],
                "files": bucket_meta[bucket_id]["files"],
            }
            for bucket_id in range(hash_bucket_count)
        ],
    }
    manifest_path = target_dir / MANIFEST_FILENAME
    with manifest_path.open("w") as mf:
        json.dump(manifest, mf, ensure_ascii=False, indent=2)
    console.log(f"[{index}] wrote manifest -> {manifest_path}")

    return written_files


class PartitionedIndex:
    def __init__(self, folder: Path):
        self.folder = folder
        manifest_path = folder / MANIFEST_FILENAME
        if not manifest_path.exists():
            raise FileNotFoundError(
                f"Manifest not found in {folder}. Did you run the convert stage after hashing?"
            )
        with manifest_path.open("r") as mf:
            raw = json.load(mf)
        self.hash_bucket_count: int = raw.get("hash_bucket_count")
        if not isinstance(self.hash_bucket_count, int) or self.hash_bucket_count <= 0:
            raise ValueError("Manifest missing valid 'hash_bucket_count'.")
        buckets_raw = raw.get("buckets", [])
        if not isinstance(buckets_raw, list) or len(buckets_raw) != self.hash_bucket_count:
            raise ValueError("Manifest 'buckets' entry inconsistent with hash_bucket_count.")
        self.bucket_map: Dict[int, Dict[str, Any]] = {}
        for entry in buckets_raw:
            bucket_id = entry.get("bucket_id")
            if not isinstance(bucket_id, int):
                raise ValueError("Manifest bucket entry missing integer bucket_id")
            files = entry.get("files", []) or []
            doc_count = entry.get("doc_count", 0)
            self.bucket_map[bucket_id] = {
                "files": [Path(f) for f in files],
                "doc_count": int(doc_count),
            }
        self.total_docs = int(raw.get("total_docs", 0))

    def iter_bucket_ids(self) -> List[int]:
        return list(range(self.hash_bucket_count))

    def bucket_doc_count(self, bucket_id: int) -> int:
        return self.bucket_map.get(bucket_id, {}).get("doc_count", 0)

    def bucket_files(self, bucket_id: int) -> List[Path]:
        entry = self.bucket_map.get(bucket_id)
        if not entry:
            return []
        return entry["files"]

    def read_bucket(self, bucket_id: int):
        import polars as pl

        files = self.bucket_files(bucket_id)
        if not files:
            return pl.DataFrame()
        dfs = []
        for rel_path in files:
            dfs.append(pl.read_parquet(self.folder / rel_path))
        if not dfs:
            return pl.DataFrame()
        return pl.concat(dfs, how="vertical_relaxed")

    def iter_buckets(self):
        for bucket_id in self.iter_bucket_ids():
            yield bucket_id, self.read_bucket(bucket_id)


def load_parquet_index(folder: Path):
    """Load all parquet shards for an index into a polars DataFrame (relaxed vertical concat).

    This helper remains for backwards compatibility; it now streams through hash buckets before
    concatenating, so prefer PartitionedIndex for chunked processing.
    """

    import polars as pl

    reader = PartitionedIndex(folder)
    dfs = []
    for _, bucket_df in reader.iter_buckets():
        if bucket_df.height == 0:
            continue
        dfs.append(bucket_df)
    if not dfs:
        raise ValueError(f"No parquet files in {folder}")
    return pl.concat(dfs, how="vertical_relaxed")
