"""Backfill a works_identified Iceberg table from its index; see the backfill
section in README.md for usage and operational details."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Callable, Iterator
from typing import Any, cast

import structlog
from elasticsearch import Elasticsearch
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg import get_rest_api_table
from core.document import Document
from core.sinks import IcebergSink
from core.source import ElasticSource
from id_minter.iceberg import REST_API_ICEBERG_CONFIG
from id_minter.id_minting_transformer import document_version
from id_minter.schemata import works_identified_row
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

BATCH_SIZE = 10_000
"""Documents per Iceberg append, matching the minter's batch size."""


class BackfillError(Exception):
    pass


def iter_documents(
    es_client: Elasticsearch, index_name: str, limit: int | None = None
) -> Iterator[dict[str, Any]]:
    """Every document in the index, through one point in time, with the source's retries."""
    source = ElasticSource(
        es_client=es_client,
        index_name=index_name,
        query={"match_all": {}},
        batch_size=min(BATCH_SIZE, limit) if limit else BATCH_SIZE,
        slice_count=1,
        parallelism=1,
    )
    try:
        for yielded, body in enumerate(source.stream_raw(), start=1):
            yield body
            if limit is not None and yielded >= limit:
                return
    finally:
        es_client.close_point_in_time(id=source.pit_id)


def to_document(body: dict[str, Any]) -> Document:
    state = body["state"]
    identifier = state["sourceIdentifier"]
    return Document(
        source_id=f"{identifier['identifierType']['id']}/{identifier['value']}",
        target_id=state["canonicalId"],
        body=body,
        version=document_version(body),
    )


def table_loader(table_name: str) -> Callable[[], IcebergTable]:
    """Loads the table afresh on each call, so a long run picks up renewed AWS credentials.

    The table must already exist: creating one here would hide a wrong name or date."""
    config = REST_API_ICEBERG_CONFIG.model_copy(update={"table_name": table_name})
    return lambda: get_rest_api_table(config, create_if_not_exists=False)


def existing_versions(table: IcebergTable) -> dict[str, int]:
    """The newest version the table holds for each work."""
    rows = table.scan(selected_fields=("id", "version")).to_arrow()
    versions: dict[str, int] = {}
    for work_id, version in zip(
        rows["id"].to_pylist(), rows["version"].to_pylist(), strict=True
    ):
        if work_id is None or version is None:
            continue
        if version > versions.get(work_id, -1):
            versions[work_id] = version
    return versions


def backfill(
    es_client: Elasticsearch,
    index_name: str,
    load_table: Callable[[], IcebergTable],
    existing: dict[str, int],
    limit: int | None = None,
) -> tuple[int, int]:
    """Append each document the table lacks, returning (rows written, documents skipped)."""
    written = skipped = 0
    batch: list[Document] = []

    def flush() -> None:
        nonlocal written
        if not batch:
            return
        result = IcebergSink(load_table(), works_identified_row).write(batch)
        if result.failed:
            # The sink has already logged the error; a backfill with a hole is not a backfill.
            raise BackfillError(
                f"Append failed after {written} rows: {result.failed[0][1]}"
            )
        written += len(result.accepted)
        logger.info("Backfill progress", rows=written, skipped=skipped)
        batch.clear()

    for body in iter_documents(es_client, index_name, limit):
        document = to_document(body)
        if existing.get(document.target_id, -1) >= (document.version or 0):
            skipped += 1
            continue
        batch.append(document)
        if len(batch) >= BATCH_SIZE:
            flush()
    flush()
    return written, skipped


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Append every work in a works-identified index to its Iceberg table"
    )
    parser.add_argument(
        "--pipeline-date",
        required=True,
        help="Pipeline whose Elasticsearch secrets and table to use, e.g. 2026-09-30",
    )
    parser.add_argument(
        "--index-date",
        help="Date suffix of the works-identified index, if it differs from the pipeline date.",
    )
    parser.add_argument(
        "--es-mode",
        choices=["private", "public"],
        default="public",
        help="Which Elasticsearch host to read from. Default: public, for a run from outside the VPC.",
    )
    parser.add_argument(
        "--api-key-name",
        default="monitoring_read_only",
        help="Secret name of the Elasticsearch API key to read with. Default: monitoring_read_only.",
    )
    parser.add_argument(
        "--table-name",
        help="Override the table name. Default: works_identified_<pipeline date with underscores>.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        metavar="N",
        help="Read only the first N documents, to smoke-test against real data. Expect one search error afterwards: closing the point in time is what stops the reader.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Load the table and count the index, but append nothing.",
    )
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error(f"--limit must be at least 1, got {args.limit}")

    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(), pipeline_step="backfill_works_identified"
        )
    )

    index_name = get_standard_index_name(
        "works-identified", args.index_date or args.pipeline_date
    )
    # Gzip cuts a 10,000-document page over the public endpoint from ~20 s to a few seconds.
    es_client = get_client(
        args.api_key_name,
        args.pipeline_date,
        cast(ElasticsearchMode, args.es_mode),
        http_compress=True,
    )
    load_table = table_loader(
        args.table_name or f"works_identified_{args.pipeline_date.replace('-', '_')}"
    )
    table = load_table()
    existing = existing_versions(table)
    logger.info(
        "Backfill target",
        index=index_name,
        documents=es_client.count(index=index_name)["count"],
        table=".".join(table.name()),
        works_in_table=len(existing),
        dry_run=args.dry_run,
    )
    if args.dry_run:
        return

    try:
        written, skipped = backfill(
            es_client, index_name, load_table, existing, limit=args.limit
        )
    except BackfillError as e:
        logger.error(str(e))
        sys.exit(1)
    logger.info("Backfill complete", rows=written, skipped=skipped)


if __name__ == "__main__":
    main()
