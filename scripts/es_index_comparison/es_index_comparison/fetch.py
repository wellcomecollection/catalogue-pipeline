from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Any

from elasticsearch import Elasticsearch, helpers
from rich.console import Console

from .source_config import ResolvedIndexSource

console = Console()

MATCH_ALL: dict[str, Any] = {"query": {"match_all": {}}}


def fetch_index(es: Elasticsearch, index: str, out_file: Path, query: dict[str, Any] | None) -> int:
    """Write every document matching the query to a gzipped NDJSON file and return the count."""
    count = 0
    with gzip.open(out_file, "wt") as out:
        for doc in helpers.scan(es, index=index, query=query or MATCH_ALL, scroll="30m"):
            out.write(json.dumps(doc) + "\n")
            count += 1
            if count % 50_000 == 0:
                console.log(f"{index}: wrote {count} docs ...")
    console.log(f"Completed fetch {index}: total {count} docs -> {out_file}")
    return count


def fetch_both(
    sources: list[ResolvedIndexSource],
    raw_dir: Path,
    queries: dict[str, dict[str, Any] | None],
) -> None:
    """Fetch each index source, using the query given for its id."""
    for source in sources:
        out_file = raw_dir / f"{source.storage_key}.ndjson.gz"
        console.log(f"Fetching {source.label} to {out_file}")
        with source.connect() as es:
            fetch_index(es, source.index, out_file, queries.get(source.id))
