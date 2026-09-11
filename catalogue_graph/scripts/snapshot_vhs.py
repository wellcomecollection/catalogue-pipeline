"""Write a parquet snapshot of the latest version of every record in a VHS.

A VHS (versioned hybrid store) keeps an index in DynamoDB and the record
bodies in S3. The DynamoDB row is the only thing that says which S3 object is
current: the bucket holds every version a record has ever had, so the snapshot
scans the table, reads the object each row points at, and writes one parquet
row per record.

This exists because a VHS becomes the only copy of its source dataset once its
adapter stops harvesting, which is happening to the CALM store under
wellcomecollection/platform#6689. The other two stores have the same shape and
will reach the same point as their sources are retired.

The snapshot is written to a `.partial` file and moved into place only on a
complete, successful run, so a file at the output path is always whole.

Usage:
    uv run python scripts/snapshot_vhs.py --store calm --output-path /tmp/calm.parquet
    uv run python scripts/snapshot_vhs.py --store calm --output-path /tmp/calm.parquet --upload-to s3://bucket/prefix/
"""

from __future__ import annotations

import argparse
import json
import os
import time
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import Any

import boto3
import pyarrow as pa
import pyarrow.parquet as pq
import structlog
from botocore.config import Config as BotocoreConfig
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.schema import Schema
from pyiceberg.types import (
    BooleanType,
    IntegerType,
    NestedField,
    StringType,
    TimestamptzType,
)

from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

BATCH_SIZE = 10_000
"""Records per parquet row group. Each batch's bodies are held in memory while
it is assembled, so this bounds peak usage rather than the record count."""

SCAN_SEGMENTS = 16
"""Parallel DynamoDB scan segments."""

FETCH_WORKERS = 32
"""Concurrent S3 GETs. The bodies are small and the run is one-off, so this is
set for a sensible wall-clock rather than to saturate anything."""

PROGRESS_LOG_EVERY = 25_000


# The first six fields match ADAPTER_STORE_ICEBERG_SCHEMA in
# adapters/utils/schemata.py, so selecting them gives a table that loads
# through the adapter store's own snapshot path. `version` and `s3_key` are
# VHS-specific provenance on top.
VHS_SNAPSHOT_ICEBERG_SCHEMA = Schema(
    NestedField(field_id=1, name="namespace", field_type=StringType(), required=True),
    NestedField(field_id=2, name="id", field_type=StringType(), required=True),
    NestedField(field_id=3, name="content", field_type=StringType(), required=False),
    NestedField(field_id=4, name="changeset", field_type=StringType(), required=False),
    NestedField(
        field_id=5, name="last_modified", field_type=TimestamptzType(), required=True
    ),
    NestedField(
        field_id=6,
        name="deleted",
        field_type=BooleanType(),
        required=False,
        default_value=False,
    ),
    NestedField(field_id=7, name="version", field_type=IntegerType(), required=True),
    NestedField(field_id=8, name="s3_key", field_type=StringType(), required=True),
)
VHS_SNAPSHOT_ARROW_SCHEMA: pa.Schema = schema_to_pyarrow(VHS_SNAPSHOT_ICEBERG_SCHEMA)


@dataclass(frozen=True)
class VHSStoreConfig:
    table_name: str
    namespace: str
    id_field: str | None = None
    """Key in the record body holding the record's own id, where the body has
    one. Used to check the DynamoDB row and the S3 object agree."""


VHS_STORES: dict[str, VHSStoreConfig] = {
    "calm": VHSStoreConfig(
        table_name="vhs-calm-adapter", namespace="calm", id_field="id"
    ),
    "sierra": VHSStoreConfig(
        table_name="vhs-sierra-sierra-adapter-20200604", namespace="sierra"
    ),
    "miro": VHSStoreConfig(table_name="vhs-sourcedata-miro", namespace="miro"),
}


@dataclass(frozen=True)
class IndexRow:
    """One DynamoDB row, pointing at the current body for a record."""

    id: str
    version: int
    bucket: str
    key: str
    deleted: bool


class SnapshotError(Exception):
    pass


def _parse_index_row(item: dict[str, Any]) -> IndexRow:
    """Read a VHS index row.

    The S3 location lives under `payload` in some stores and `location` in
    others. The key is taken verbatim and never rebuilt from the version,
    because the CALM deletion checker bumps the version without writing a new
    object, leaving the two disagreeing for deleted records.
    """
    location = item.get("payload") or item.get("location")
    if not location:
        raise SnapshotError(f"Row {item.get('id')} has no payload or location")

    return IndexRow(
        id=item["id"],
        version=int(item["version"]),
        bucket=location["bucket"],
        key=location["key"],
        deleted=bool(item.get("isDeleted", False)),
    )


def scan_index(dynamodb_resource: Any, table_name: str) -> list[IndexRow]:
    """Read every row of the VHS index table, scanning segments in parallel."""
    client = dynamodb_resource.meta.client

    def scan_segment(segment: int) -> list[IndexRow]:
        rows: list[IndexRow] = []
        paginator = client.get_paginator("scan")
        pages = paginator.paginate(
            TableName=table_name, Segment=segment, TotalSegments=SCAN_SEGMENTS
        )
        for page in pages:
            rows.extend(_parse_index_row(item) for item in page["Items"])
        return rows

    started_at = time.time()
    with ThreadPoolExecutor(max_workers=SCAN_SEGMENTS) as pool:
        segments = pool.map(scan_segment, range(SCAN_SEGMENTS))
        rows = [row for segment_rows in segments for row in segment_rows]

    logger.info(
        "Index scanned",
        table_name=table_name,
        rows=len(rows),
        seconds=round(time.time() - started_at),
    )
    if not rows:
        raise SnapshotError(
            f"Table {table_name} returned 0 rows. This is almost certainly an "
            "error rather than an empty store."
        )
    return rows


def _fetch_row(s3_client: Any, config: VHSStoreConfig, row: IndexRow) -> dict[str, Any]:
    """Read one record body and turn it into a snapshot row."""
    try:
        response = s3_client.get_object(Bucket=row.bucket, Key=row.key)
    except Exception as error:
        raise SnapshotError(
            f"Could not read {row.bucket}/{row.key} for record {row.id}: {error}"
        ) from error

    content = response["Body"].read().decode("utf8")

    try:
        body = json.loads(content)
    except json.JSONDecodeError as error:
        raise SnapshotError(
            f"Body at {row.bucket}/{row.key} for record {row.id} is not JSON: {error}"
        ) from error

    if config.id_field is not None:
        body_id = body.get(config.id_field)
        if body_id != row.id:
            raise SnapshotError(
                f"Record {row.id} points at {row.key}, whose {config.id_field} "
                f"is {body_id!r}. The index and the body disagree."
            )

    return {
        "namespace": config.namespace,
        "id": row.id,
        "content": content,
        "changeset": None,
        "last_modified": response["LastModified"],
        "deleted": row.deleted,
        "version": row.version,
        "s3_key": row.key,
    }


def _iter_batches(rows: list[IndexRow], size: int) -> Iterator[list[IndexRow]]:
    for start in range(0, len(rows), size):
        yield rows[start : start + size]


def write_snapshot(
    s3_client: Any,
    config: VHSStoreConfig,
    rows: list[IndexRow],
    output_path: str,
) -> int:
    """Fetch every record body and write the snapshot, returning the row count.

    Writes to a `.partial` file moved into place only once the whole run has
    succeeded, so an interrupted run cannot leave a truncated snapshot behind
    for someone to mistake for a complete one.
    """
    partial_path = f"{output_path}.partial"
    written = 0
    started_at = time.time()
    logged_at = 0

    try:
        with (
            pq.ParquetWriter(partial_path, VHS_SNAPSHOT_ARROW_SCHEMA) as writer,
            ThreadPoolExecutor(max_workers=FETCH_WORKERS) as pool,
        ):
            for batch in _iter_batches(rows, BATCH_SIZE):
                records = list(
                    pool.map(lambda row: _fetch_row(s3_client, config, row), batch)
                )
                writer.write_table(
                    pa.Table.from_pylist(records, schema=VHS_SNAPSHOT_ARROW_SCHEMA)
                )
                written += len(records)

                if written - logged_at >= PROGRESS_LOG_EVERY:
                    elapsed = max(time.time() - started_at, 1e-6)
                    logger.info(
                        "Snapshot progress",
                        written=written,
                        total=len(rows),
                        records_per_second=round(written / elapsed, 1),
                    )
                    logged_at = written
    except BaseException:
        # A partial file left next to the output is the one thing that could
        # be mistaken for a snapshot, so never leave one behind.
        if os.path.exists(partial_path):
            os.remove(partial_path)
        raise

    os.replace(partial_path, output_path)
    return written


def verify_snapshot(output_path: str, rows: list[IndexRow]) -> None:
    """Check the written file against the index it was built from."""
    table = pq.read_table(output_path, columns=["id", "deleted"])

    if table.num_rows != len(rows):
        raise SnapshotError(
            f"Snapshot has {table.num_rows} rows but the index had {len(rows)}"
        )

    snapshot_ids = set(table.column("id").to_pylist())
    missing = {row.id for row in rows} - snapshot_ids
    if missing:
        raise SnapshotError(
            f"{len(missing)} record(s) in the index are not in the snapshot, "
            f"for example {sorted(missing)[:3]}"
        )

    logger.info(
        "Snapshot verified",
        path=output_path,
        rows=table.num_rows,
        deleted=sum(bool(value) for value in table.column("deleted").to_pylist()),
        bytes=os.path.getsize(output_path),
    )


def upload_snapshot(s3_client: Any, output_path: str, destination: str) -> str:
    """Copy the finished snapshot to S3, returning the URI it was written to."""
    if not destination.startswith("s3://"):
        raise ValueError(f"--upload-to must be an s3:// URI, got {destination!r}")

    bucket, _, prefix = destination[len("s3://") :].partition("/")
    key = f"{prefix.rstrip('/')}/{os.path.basename(output_path)}".lstrip("/")

    s3_client.upload_file(Filename=output_path, Bucket=bucket, Key=key)
    uri = f"s3://{bucket}/{key}"
    logger.info("Snapshot uploaded", uri=uri)
    return uri


def snapshot_vhs(
    store: str,
    output_path: str,
    *,
    upload_to: str | None = None,
    limit: int | None = None,
    session: Any | None = None,
) -> int:
    config = VHS_STORES[store]
    session = session or boto3.Session()

    rows = scan_index(session.resource("dynamodb"), config.table_name)

    if limit is not None:
        logger.warning(
            "Smoke test only. The file this writes covers part of the store and "
            "is not a snapshot of it.",
            limit=limit,
            index_rows=len(rows),
        )
        rows = rows[:limit]

    # botocore pools 10 connections by default, so most of the fetch workers
    # spend the run discarding connections they could have reused. A handful
    # still spill while the pool fills, which raising this further does not
    # change.
    s3_client = session.client(
        "s3", config=BotocoreConfig(max_pool_connections=FETCH_WORKERS)
    )
    written = write_snapshot(s3_client, config, rows, output_path)
    verify_snapshot(output_path, rows)

    if upload_to is not None:
        if limit is not None:
            raise ValueError("--limit writes a partial file, so it cannot be uploaded")
        upload_snapshot(s3_client, output_path, upload_to)

    return written


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Write a parquet snapshot of the latest version of every record in a VHS"
    )
    parser.add_argument(
        "--store",
        required=True,
        choices=sorted(VHS_STORES),
        help="Which VHS to snapshot",
    )
    parser.add_argument(
        "--output-path",
        required=True,
        metavar="PATH",
        help="Where to write the parquet file",
    )
    parser.add_argument(
        "--upload-to",
        metavar="S3_URI",
        help="Optional s3:// prefix to copy the finished snapshot to",
    )
    parser.add_argument(
        "--limit",
        type=int,
        metavar="N",
        help="Fetch only the first N records, to smoke-test against real data. The file this writes is not a snapshot and cannot be uploaded.",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="snapshot_vhs")
    )

    written = snapshot_vhs(
        args.store, args.output_path, upload_to=args.upload_to, limit=args.limit
    )
    logger.info("Snapshot complete", store=args.store, rows=written)


if __name__ == "__main__":
    main()
