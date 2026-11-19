from __future__ import annotations

import gzip
import hashlib
import io
import json
from collections import Counter, defaultdict
from itertools import batched
from pathlib import Path

import polars as pl
from rich.console import Console

MANIFEST_FILENAME = "manifest.json"

console = Console()


def _hash_bucket_for_id(doc_id: str, bucket_count: int) -> int:
    digest = hashlib.blake2s(str(doc_id).encode(), digest_size=8).digest()
    return int.from_bytes(digest, "big") % bucket_count


def ndjson_gz_to_parquet_shards(
    index: str,
    ndjson_path: Path,
    out_parent: Path,
    chunk_size: int,
    hash_bucket_count: int,
    bucket_filter: set[int] | None = None,
) -> list[Path]:
    """Split a gzipped NDJSON file of Elasticsearch hits into Parquet files grouped by hash bucket.

    The file is read chunk_size documents at a time, and each chunk writes one
    Parquet file per bucket.
    """
    target_dir = out_parent / index
    bucket_files: dict[int, list[str]] = defaultdict(list)
    doc_counts: Counter[int] = Counter()
    written_files: list[Path] = []

    with gzip.open(ndjson_path, "rb") as ndjson:
        for part, lines in enumerate(batched(ndjson, chunk_size)):
            df = pl.read_ndjson(io.BytesIO(b"".join(lines)), infer_schema_length=None)
            buckets = [_hash_bucket_for_id(doc_id, hash_bucket_count) for doc_id in df["_id"]]
            df = df.with_columns(pl.Series("bucket", buckets, dtype=pl.Int64))
            if bucket_filter is not None:
                df = df.filter(pl.col("bucket").is_in(sorted(bucket_filter)))

            partitions = df.partition_by("bucket", as_dict=True, include_key=False)
            for (bucket_id,), bucket_df in partitions.items():
                relative_path = Path(f"bucket_{bucket_id:05d}") / f"part-{part:05d}.parquet"
                out_path = target_dir / relative_path
                out_path.parent.mkdir(parents=True, exist_ok=True)
                bucket_df.write_parquet(out_path)
                bucket_files[bucket_id].append(str(relative_path))
                doc_counts[bucket_id] += bucket_df.height
                written_files.append(out_path)
            console.log(f"[{index}] converted {doc_counts.total()} docs")

    manifest = {
        "version": 1,
        "index": index,
        "hash_bucket_count": hash_bucket_count,
        "loading_chunk_size": chunk_size,
        "total_docs": doc_counts.total(),
        "buckets": [
            {
                "bucket_id": bucket_id,
                "doc_count": doc_counts[bucket_id],
                "files": bucket_files[bucket_id],
            }
            for bucket_id in range(hash_bucket_count)
        ],
    }
    target_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = target_dir / MANIFEST_FILENAME
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    console.log(f"[{index}] wrote manifest -> {manifest_path}")

    return written_files


class PartitionedIndex:
    def __init__(self, folder: Path):
        manifest_path = folder / MANIFEST_FILENAME
        if not manifest_path.exists():
            raise FileNotFoundError(
                f"Manifest not found in {folder}. Did you run the convert stage after hashing?"
            )
        manifest = json.loads(manifest_path.read_text())
        self.folder = folder
        self.hash_bucket_count: int = manifest["hash_bucket_count"]
        self.total_docs: int = manifest["total_docs"]
        self.buckets: dict[int, dict] = {b["bucket_id"]: b for b in manifest["buckets"]}

    def iter_bucket_ids(self) -> list[int]:
        return list(range(self.hash_bucket_count))

    def bucket_doc_count(self, bucket_id: int) -> int:
        return self.buckets[bucket_id]["doc_count"]

    def read_bucket(self, bucket_id: int) -> pl.DataFrame:
        files = self.buckets[bucket_id]["files"]
        if not files:
            return pl.DataFrame()
        frames = [pl.read_parquet(self.folder / file) for file in files]
        return pl.concat(frames, how="vertical_relaxed")
