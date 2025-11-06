from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Dict, Any, List, Tuple
from elasticsearch import helpers, Elasticsearch
from rich.console import Console

from .es_client import build_client
from .source_config import ResolvedIndexSource

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
    sources: List[ResolvedIndexSource],
    raw_dir: Path,
    query: Dict[str, Any] | None,
) -> Dict[str, Path]:
    """Fetch documents for each index source, instantiating clients per cluster."""

    results: Dict[str, Path] = {}
    clients: Dict[Tuple[str, str, str], Elasticsearch] = {}

    try:
        for source in sources:
            client_key = (source.cluster_id, source.cloud_id, source.api_key)
            if client_key not in clients:
                clients[client_key] = build_client(source.cloud_id, source.api_key)
            es = clients[client_key]

            out_file = raw_dir / f"{source.storage_key}.ndjson.gz"
            console.log(
                f"Fetching index {source.index} (source={source.id}, cluster={source.cluster_id}) to {out_file}"
            )
            fetch_index(es, source.index, out_file, query)
            results[source.id] = out_file
    finally:
        for es in clients.values():
            try:
                es.close()
            except Exception:
                pass

    return results
