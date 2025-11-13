from __future__ import annotations

import gzip
import json
import os
from pathlib import Path
from typing import Dict, Any

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
from rich.console import Console
import polars as pl
import tqdm

console = Console()


def ndjson_gz_to_parquet_shards(
    index: str,
    ndjson_path: Path,
    out_parent: Path,
    chunk_size: int,
) -> list[Path]:
    """Convert a gzipped NDJSON (Elasticsearch hits) to a directory of parquet shards.

    We build a mapping keyed by _id with entire hit document retained to mimic original notebook logic
    where nested access into _source is needed later.
    """
    target_dir = out_parent / index
    target_dir.mkdir(parents=True, exist_ok=True)

    docs: Dict[str, Dict[str, Any]] = {}
    shards: list[Path] = []

    def flush(shard_key: str):
        nonlocal docs
        if not docs:
            return
        df = pd.DataFrame.from_dict(docs, orient="index")
        table = pa.Table.from_pandas(df)
        out_path = target_dir / f"{shard_key}.parquet"
        pq.write_table(table, out_path)
        console.log(f"Wrote shard {out_path} rows={len(docs)}")
        shards.append(out_path)
        docs = {}

    with gzip.open(ndjson_path, "rt") as fh:
        for i, line in enumerate(fh):
            doc = json.loads(line)
            _id = doc.get("_id")
            if _id is None:
                continue
            docs[_id] = doc
            if (i + 1) % chunk_size == 0:
                flush(str(i + 1))
        # flush remaining
        flush("final")

    return shards


def load_parquet_index(folder: Path):
    """Load all parquet shards for an index into a polars DataFrame (relaxed vertical concat)."""
    files = [f for f in os.listdir(folder) if f.endswith(".parquet")]
    dfs = []
    for f in tqdm.tqdm(files, desc=f"load {folder.name}"):
        dfs.append(pl.read_parquet(folder / f))
    if not dfs:
        raise ValueError(f"No parquet files in {folder}")
    return pl.concat(dfs, how="vertical_relaxed")
