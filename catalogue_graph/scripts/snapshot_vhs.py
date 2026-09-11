"""Write a parquet snapshot of the latest version of every record in a VHS.

A VHS (versioned hybrid store) keeps an index in DynamoDB and the record
bodies in S3. The DynamoDB row is the only thing that says which S3 object is
current: the bucket holds every version a record has ever had, so the snapshot
scans the table, reads the object each row points at, and writes one parquet
row per record.

This exists because a VHS stops being rebuildable once its adapter stops
harvesting, which is happening to the CALM store under
wellcomecollection/platform#6689. The other two stores have the same shape and
reach the same point as their sources are retired.

The snapshot is written to a `.partial` file and moved into place only on a
complete, successful run, so a file at the output path is always whole.

Usage:
    uv run python scripts/snapshot_vhs.py --store calm --output-path /tmp/calm.parquet
    uv run python scripts/snapshot_vhs.py --store calm --output-path /tmp/calm.parquet --upload-to s3://bucket/prefix/
"""

from __future__ import annotations

import argparse
import base64
import decimal
import json
import os
import time
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import UTC, datetime
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

EPOCH = datetime(1970, 1, 1, tzinfo=UTC)
"""Stand-in `last_modified` for a row whose object could not be read. The
column is non-optional, and a row like that is reported separately anyway."""


# The first six fields match ADAPTER_STORE_ICEBERG_SCHEMA in
# adapters/utils/schemata.py, so selecting them gives a table that loads
# through the adapter store's own snapshot path. test_snapshot_vhs.py holds
# the two to that promise. The rest is VHS-specific provenance.
#
# `last_modified` is the S3 object's timestamp, so for a record marked deleted
# it is when the last live body was written rather than when the deletion was
# recorded. The deletion is only ever a DynamoDB-side fact.
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
    # The whole DynamoDB row, verbatim. Some stores keep state here that exists
    # nowhere else: Miro rows carry `isClearedForCatalogueAPI`, `events` and
    # `overrides`, which are curated by hand through scripts/suppress_miro and
    # are not in the S3 body. Naming the fields we know about would quietly
    # drop the ones we do not.
    NestedField(field_id=9, name="index_row", field_type=StringType(), required=True),
)
VHS_SNAPSHOT_ARROW_SCHEMA: pa.Schema = schema_to_pyarrow(VHS_SNAPSHOT_ICEBERG_SCHEMA)


@dataclass(frozen=True)
class VHSStoreConfig:
    table_name: str
    namespace: str
    id_field: str | None = None
    """Key in the record body holding the record's own id, where the body has
    one. Used to check the DynamoDB row and the S3 object agree."""
    deleted_table_name: str | None = None
    """Companion table holding records deleted from the main one. Sierra moved
    its pre-2018 deletions out rather than marking them in place, so a snapshot
    that reads only the main table silently omits them."""


VHS_STORES: dict[str, VHSStoreConfig] = {
    "calm": VHSStoreConfig(
        table_name="vhs-calm-adapter", namespace="calm", id_field="id"
    ),
    "sierra": VHSStoreConfig(
        table_name="vhs-sierra-sierra-adapter-20200604",
        namespace="sierra",
        deleted_table_name="vhs-sierra-sierra-adapter-20200604-deleted",
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
    raw: str
    """The whole row as JSON, so nothing a store keeps here is lost."""


class SnapshotError(Exception):
    pass


def _json_default(value: Any) -> Any:
    """Render the types boto3's deserialiser produces that JSON has no place for."""
    if isinstance(value, decimal.Decimal):
        return int(value) if value == int(value) else float(value)
    if isinstance(value, set):
        return sorted(value)
    if isinstance(value, bytes | bytearray):
        return base64.b64encode(value).decode("ascii")
    raise TypeError(f"Cannot serialise {type(value).__name__} to JSON")


def _parse_index_row(item: dict[str, Any], *, deleted: bool = False) -> IndexRow:
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
        deleted=deleted or bool(item.get("isDeleted", False)),
        raw=json.dumps(item, default=_json_default, sort_keys=True),
    )


def _scan_table(client: Any, table_name: str, *, deleted: bool) -> list[IndexRow]:
    def scan_segment(segment: int) -> list[IndexRow]:
        rows: list[IndexRow] = []
        paginator = client.get_paginator("scan")
        pages = paginator.paginate(
            TableName=table_name, Segment=segment, TotalSegments=SCAN_SEGMENTS
        )
        for page in pages:
            rows.extend(
                _parse_index_row(item, deleted=deleted) for item in page["Items"]
            )
        return rows

    started_at = time.time()
    with ThreadPoolExecutor(max_workers=SCAN_SEGMENTS) as pool:
        segments = pool.map(scan_segment, range(SCAN_SEGMENTS))
        rows = [row for segment_rows in segments for row in segment_rows]

    logger.info(
        "Table scanned",
        table_name=table_name,
        rows=len(rows),
        seconds=round(time.time() - started_at),
    )
    return rows


def scan_index(dynamodb_resource: Any, config: VHSStoreConfig) -> list[IndexRow]:
    """Read every row of the store's index, including its deleted companion."""
    client = dynamodb_resource.meta.client

    rows = _scan_table(client, config.table_name, deleted=False)
    if not rows:
        raise SnapshotError(
            f"Table {config.table_name} returned 0 rows. This is almost "
            "certainly an error rather than an empty store."
        )

    if config.deleted_table_name is not None:
        rows.extend(_scan_table(client, config.deleted_table_name, deleted=True))

    return rows


def _fetch_row(s3_client: Any, config: VHSStoreConfig, row: IndexRow) -> dict[str, Any]:
    """Read one record body and turn it into a snapshot row.

    A row that cannot be read keeps its index fields and gets a null `content`,
    so one bad object does not cost a 17-minute run over the other 408,709.
    Whether the run may finish with any of these is the caller's decision.
    """
    content: str | None = None
    last_modified = None

    try:
        response = s3_client.get_object(Bucket=row.bucket, Key=row.key)
        last_modified = response["LastModified"]
        content = response["Body"].read().decode("utf8")
        body = json.loads(content)

        if config.id_field is not None and body.get(config.id_field) != row.id:
            raise SnapshotError(
                f"its {config.id_field} is {body.get(config.id_field)!r}, so "
                "the index and the body disagree"
            )
    except Exception as error:
        logger.error(
            "Could not read record body",
            record_id=row.id,
            bucket=row.bucket,
            key=row.key,
            error=str(error),
        )
        content = None

    return {
        "namespace": config.namespace,
        "id": row.id,
        "content": content,
        "changeset": None,
        # Falls back to the epoch only for a row whose object could not be
        # read, which the caller has to account for before the run counts.
        "last_modified": last_modified or EPOCH,
        "deleted": row.deleted,
        "version": row.version,
        "s3_key": row.key,
        "index_row": row.raw,
    }


def _iter_batches(rows: list[IndexRow], size: int) -> Iterator[list[IndexRow]]:
    for start in range(0, len(rows), size):
        yield rows[start : start + size]


def write_snapshot(
    s3_client: Any,
    config: VHSStoreConfig,
    rows: list[IndexRow],
    output_path: str,
    *,
    allow_unreadable: int = 0,
) -> int:
    """Fetch every record body and write the snapshot, returning the row count.

    Writes to a `.partial` file moved into place only once the whole run has
    succeeded, so an interrupted run cannot leave a truncated snapshot behind
    for someone to mistake for a complete one.

    Unreadable bodies are collected rather than raised on, so a run surfaces
    every bad record at once instead of one per 17-minute attempt, and then
    fails at the end unless `allow_unreadable` covers them.
    """
    partial_path = f"{output_path}.partial"
    written = 0
    started_at = time.time()
    logged_at = 0
    unreadable = 0

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
                unreadable += sum(1 for r in records if r["content"] is None)

                if written - logged_at >= PROGRESS_LOG_EVERY:
                    elapsed = max(time.time() - started_at, 1e-6)
                    logger.info(
                        "Snapshot progress",
                        written=written,
                        total=len(rows),
                        records_per_second=round(written / elapsed, 1),
                    )
                    logged_at = written

        if unreadable > allow_unreadable:
            raise SnapshotError(
                f"{unreadable} record(s) had no readable body, over the "
                f"{allow_unreadable} allowed. Each one was logged above. Fix "
                "them and run again, or pass --allow-unreadable to accept a "
                "snapshot with null content for those records."
            )
        if unreadable:
            logger.warning("Snapshot has records with no body", unreadable=unreadable)

        # Inside the cleanup scope: a move that fails, say onto an unwritable
        # path, would otherwise leave the partial file it was meant to consume.
        os.replace(partial_path, output_path)
    except BaseException:
        # A partial file left next to the output is the one thing that could
        # be mistaken for a snapshot, so never leave one behind.
        if os.path.exists(partial_path):
            os.remove(partial_path)
        raise

    return written


def verify_snapshot(output_path: str, rows: list[IndexRow]) -> None:
    """Re-read the written file and check it against the index it came from.

    This reads the file back from disk rather than trusting the writer, so it
    catches a truncated or unreadable parquet as much as a miscount.
    """
    table = pq.read_table(output_path, columns=["id", "deleted", "content"])

    if table.num_rows != len(rows):
        raise SnapshotError(
            f"Snapshot has {table.num_rows} rows but the index had {len(rows)}"
        )

    snapshot_ids = set(table.column("id").to_pylist())
    if snapshot_ids != {row.id for row in rows}:
        raise SnapshotError(
            "The ids in the snapshot are not the ids in the index, despite the "
            "counts matching"
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
    allow_unreadable: int = 0,
    session: Any | None = None,
) -> int:
    config = VHS_STORES[store]

    if limit is not None:
        if limit < 1:
            raise ValueError(f"--limit must be at least 1, got {limit}")
        if upload_to is not None:
            raise ValueError("--limit writes a partial file, so it cannot be uploaded")

    session = session or boto3.Session()

    rows = scan_index(session.resource("dynamodb"), config)

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
    written = write_snapshot(
        s3_client, config, rows, output_path, allow_unreadable=allow_unreadable
    )
    verify_snapshot(output_path, rows)

    if upload_to is not None:
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
    parser.add_argument(
        "--allow-unreadable",
        type=int,
        default=0,
        metavar="N",
        help="Finish the run even if up to N records have no readable body. Those rows keep their index fields and get null content.",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="snapshot_vhs")
    )

    written = snapshot_vhs(
        args.store,
        args.output_path,
        upload_to=args.upload_to,
        limit=args.limit,
        allow_unreadable=args.allow_unreadable,
    )
    logger.info("Snapshot complete", store=args.store, rows=written)


if __name__ == "__main__":
    main()
