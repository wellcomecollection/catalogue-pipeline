from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Dict, Any
from elasticsearch import helpers, Elasticsearch
from rich.console import Console

console = Console()


def fetch_index(
    es: Elasticsearch,
    index: str,
    out_file: Path,
    query: Dict[str, Any] | None,
    scroll: str = "30m",
) -> int:
    """Stream documents from index into a gzip'd NDJSON file.

    Returns number of hits written.
    """
    q = query if query else {"query": {"match_all": {}}}
    count = 0
    with gzip.open(out_file, "wt") as fh:
        for doc in helpers.scan(es, index=index, query=q, scroll=scroll):
            fh.write(json.dumps(doc) + "\n")
            count += 1
            if count % 50_000 == 0:
                console.log(f"{index}: wrote {count} docs ...")
    console.log(f"Completed fetch {index}: total {count} docs -> {out_file}")
    return count


def fetch_both(
    es: Elasticsearch,
    indexes: list[str],
    raw_dir: Path,
    query: Dict[str, Any] | None,
) -> Dict[str, Path]:
    results: Dict[str, Path] = {}
    for idx in indexes:
        out_file = raw_dir / f"{idx}.ndjson.gz"
        console.log(f"Fetching index {idx} to {out_file}")
        fetch_index(es, idx, out_file, query)
        results[idx] = out_file
    return results
