from __future__ import annotations

from contextlib import ExitStack
from pathlib import Path
from typing import Any

from elasticsearch import Elasticsearch, helpers
from rich.console import Console

from .buckets import BucketWriter
from .source_config import ResolvedIndexSource

console = Console()


def fetch_index(
    es: Elasticsearch, index: str, writer: BucketWriter, queries: list[dict[str, Any]]
) -> int:
    """Write every document matching any of the queries and return the count."""
    count = 0
    for query in queries:
        for hit in helpers.scan(es, index=index, query=query, scroll="30m"):
            writer.write(hit["_id"], hit["_source"])
            count += 1
            if count % 50_000 == 0:
                console.log(f"{index}: wrote {count} docs ...")
    console.log(f"Completed fetch {index}: total {count} docs")
    return count


def fetch_both(
    sources: list[ResolvedIndexSource],
    docs_dir: Path,
    queries: dict[str, list[dict[str, Any]]],
    bucket_count: int,
) -> None:
    """Fetch each index source, using the queries given for its id.

    The fetched files replace the earlier ones only when every source has been
    fetched, so the sources on disk always come from the same run.
    """
    with ExitStack() as writers:
        for source in sources:
            folder = docs_dir / source.storage_key
            console.log(f"Fetching {source.label} to {folder}")
            writer = writers.enter_context(BucketWriter(folder, bucket_count))
            with source.cluster.connect() as es:
                fetch_index(es, source.index, writer, queries[source.id])
