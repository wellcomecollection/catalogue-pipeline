from __future__ import annotations

from pathlib import Path
from typing import Any

from elasticsearch import Elasticsearch, helpers
from rich.console import Console

from .buckets import BucketWriter
from .source_config import ResolvedIndexSource

console = Console()

MATCH_ALL: dict[str, Any] = {"query": {"match_all": {}}}


def fetch_index(
    es: Elasticsearch, index: str, folder: Path, query: dict[str, Any] | None, bucket_count: int
) -> int:
    """Write every document matching the query into bucket files and return the count."""
    count = 0
    with BucketWriter(folder, bucket_count) as writer:
        for hit in helpers.scan(es, index=index, query=query or MATCH_ALL, scroll="30m"):
            writer.write(hit["_id"], hit["_source"])
            count += 1
            if count % 50_000 == 0:
                console.log(f"{index}: wrote {count} docs ...")
    console.log(f"Completed fetch {index}: total {count} docs -> {folder}")
    return count


def fetch_both(
    sources: list[ResolvedIndexSource],
    docs_dir: Path,
    queries: dict[str, dict[str, Any] | None],
    bucket_count: int,
) -> None:
    """Fetch each index source, using the query given for its id."""
    for source in sources:
        folder = docs_dir / source.storage_key
        console.log(f"Fetching {source.label} to {folder}")
        with source.connect() as es:
            fetch_index(es, source.index, folder, queries.get(source.id), bucket_count)
