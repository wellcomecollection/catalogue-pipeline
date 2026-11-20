from __future__ import annotations

from pathlib import Path
from typing import Any

from elasticsearch import Elasticsearch, helpers
from rich.console import Console

from .buckets import BucketWriter
from .source_config import ResolvedIndexSource

console = Console()


def fetch_index(
    es: Elasticsearch, index: str, folder: Path, queries: list[dict[str, Any]], bucket_count: int
) -> int:
    """Write every document matching any of the queries into bucket files and return the count."""
    count = 0
    with BucketWriter(folder, bucket_count) as writer:
        for query in queries:
            for hit in helpers.scan(es, index=index, query=query, scroll="30m"):
                writer.write(hit["_id"], hit["_source"])
                count += 1
                if count % 50_000 == 0:
                    console.log(f"{index}: wrote {count} docs ...")
    console.log(f"Completed fetch {index}: total {count} docs -> {folder}")
    return count


def fetch_both(
    sources: list[ResolvedIndexSource],
    docs_dir: Path,
    queries: dict[str, list[dict[str, Any]]],
    bucket_count: int,
) -> None:
    """Fetch each index source, using the queries given for its id."""
    for source in sources:
        folder = docs_dir / source.storage_key
        console.log(f"Fetching {source.label} to {folder}")
        with source.cluster.connect() as es:
            fetch_index(es, source.index, folder, queries[source.id], bucket_count)
