"""Append every work in a works-identified index to its works_identified Iceberg table.

The id minter writes to the table only from the moment its Iceberg writes are
turned on, so a pipeline switched on mid-life has a table covering a fraction of
its index. This reads the index and appends one row per work, built the same
way the minter builds them, so the version on each row is the one the minter
would have written. Nothing is minted, the index is not written to, and nothing
is published downstream.

Rows are appended, never replaced, so a rerun after a failure duplicates the
works written before it stopped. Readers take the highest version then the
latest last_modified per id, so duplicates are harmless; they just cost space.

Usage:
    AWS_PROFILE=platform-developer uv run python scripts/backfill_works_identified.py --pipeline-date 2026-09-30 --es-mode public --dry-run
    AWS_PROFILE=platform-developer uv run python scripts/backfill_works_identified.py --pipeline-date 2026-09-30 --es-mode public
"""

from __future__ import annotations

import argparse
import os
import sys
from collections.abc import Iterator
from typing import Any, cast

import structlog
from elasticsearch import Elasticsearch
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg import RestApiIcebergTableConfig, get_rest_api_table
from core.document import Document
from core.sinks import IcebergSink
from id_minter.id_minting_transformer import document_version
from id_minter.schemata import (
    WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    WORKS_IDENTIFIED_SORT_ORDER,
    works_identified_row,
)
from utils.elasticsearch import ElasticsearchMode, get_client, get_standard_index_name
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

BATCH_SIZE = 10_000
"""Documents per Iceberg append, matching the minter's batch size."""

PIT_KEEP_ALIVE = "15m"


class BackfillError(Exception):
    pass


def iter_documents(
    es_client: Elasticsearch, index_name: str, limit: int | None = None
) -> Iterator[dict[str, Any]]:
    """Every document in the index, through a point in time so a long read sees one snapshot."""
    pit_id = es_client.open_point_in_time(index=index_name, keep_alive=PIT_KEEP_ALIVE)[
        "id"
    ]
    yielded = 0
    search_after: list[Any] | None = None
    try:
        while True:
            body: dict[str, Any] = {
                "query": {"match_all": {}},
                "size": BATCH_SIZE,
                "pit": {"id": pit_id, "keep_alive": PIT_KEEP_ALIVE},
                "sort": [{"_shard_doc": "asc"}],
            }
            if search_after is not None:
                body["search_after"] = search_after
            result = es_client.search(body=body)
            hits = result["hits"]["hits"]
            if not hits:
                return
            pit_id = result.get("pit_id", pit_id)
            for hit in hits:
                yield hit["_source"]
                yielded += 1
                if limit is not None and yielded >= limit:
                    return
            search_after = hits[-1]["sort"]
    finally:
        es_client.close_point_in_time(id=pit_id)


def to_document(body: dict[str, Any]) -> Document:
    state = body["state"]
    identifier = state["sourceIdentifier"]
    return Document(
        source_id=f"{identifier['identifierType']['id']}/{identifier['value']}",
        target_id=state["canonicalId"],
        body=body,
        version=document_version(body),
    )


def get_table(pipeline_date: str, table_name: str | None) -> IcebergTable:
    """The pipeline's table, named as the minter's terraform names it unless overridden."""
    config = RestApiIcebergTableConfig(
        table_name=table_name or f"works_identified_{pipeline_date.replace('-', '_')}",
        namespace=os.getenv(
            "WORKS_IDENTIFIED_NAMESPACE", "wellcomecollection_catalogue"
        ),
        iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
        sort_order=WORKS_IDENTIFIED_SORT_ORDER,
        s3_tables_bucket=os.getenv(
            "S3_TABLES_BUCKET", "wellcomecollection-platform-catalogue-pipeline"
        ),
        region=os.getenv("AWS_REGION", "eu-west-1"),
        account_id=os.getenv("AWS_ACCOUNT_ID"),
    )
    # The table must already exist: creating one here would hide a wrong name or date.
    return get_rest_api_table(config, create_if_not_exists=False)


def backfill(
    es_client: Elasticsearch,
    index_name: str,
    table: IcebergTable,
    limit: int | None = None,
) -> int:
    """Append every document in the index to the table, returning the row count."""
    sink = IcebergSink(table, works_identified_row)
    written = 0
    batch: list[Document] = []

    def flush() -> None:
        nonlocal written
        if not batch:
            return
        result = sink.write(batch)
        if result.failed:
            # The sink has already logged the error; a backfill with a hole is not a backfill.
            raise BackfillError(
                f"Append failed after {written} rows: {result.failed[0][1]}"
            )
        written += len(result.accepted)
        logger.info("Backfill progress", rows=written)
        batch.clear()

    for body in iter_documents(es_client, index_name, limit):
        batch.append(to_document(body))
        if len(batch) >= BATCH_SIZE:
            flush()
    flush()
    return written


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Append every work in a works-identified index to its Iceberg table"
    )
    parser.add_argument(
        "--pipeline-date",
        required=True,
        help="Pipeline whose works-identified index and table to use, e.g. 2026-09-30",
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
        help="Append only the first N documents, to smoke-test against real data.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Load the table and count the index, but append nothing.",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(), pipeline_step="backfill_works_identified"
        )
    )

    index_name = get_standard_index_name("works-identified", args.pipeline_date)
    es_client = get_client(
        args.api_key_name, args.pipeline_date, cast(ElasticsearchMode, args.es_mode)
    )
    table = get_table(args.pipeline_date, args.table_name)
    count = es_client.count(index=index_name)["count"]
    logger.info(
        "Backfill target",
        index=index_name,
        documents=count,
        table=".".join(table.name()),
        snapshots=len(table.metadata.snapshots),
        dry_run=args.dry_run,
    )
    if args.dry_run:
        return

    try:
        written = backfill(es_client, index_name, table, limit=args.limit)
    except BackfillError as e:
        logger.error(str(e))
        sys.exit(1)
    logger.info("Backfill complete", rows=written)


if __name__ == "__main__":
    main()
