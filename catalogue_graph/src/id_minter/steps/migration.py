#!/usr/bin/env python
"""Migration step — ECS task and CLI entry points.

Downloads a parquet export from S3 and bulk-loads it into the new
two-table identifier schema (RFC 083) using LOAD DATA LOCAL INFILE.

Locally:
    python -m id_minter.steps.migration --export-date 2026-02-26 --truncate

ECS (Step Functions):
    python -m id_minter.steps.migration \
        --use-ecs --event '{"export_date":"2026-02-26","truncate":true}'
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import tempfile
from pathlib import Path
from typing import Any

import boto3
import pymysql
import structlog

from id_minter.config import ID_MINTER_CONFIG, IdMinterConfig
from id_minter.database import apply_migrations
from id_minter.migrate import migrate_from_parquet
from id_minter.models.step_events import MigrationRequest, MigrationResponse
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import run_ecs_handler

logger = structlog.get_logger(__name__)


def download_parquet_from_s3(
    request: MigrationRequest,
    dest_dir: Path,
) -> Path:
    """Download parquet files from the S3 export into *dest_dir*.

    Returns the path to the directory containing the parquet files.
    """
    s3_prefix = f"exports/{request.cluster_name}/{request.export_date}/"
    logger.info(
        "Listing S3 objects",
        bucket=request.s3_bucket,
        prefix=s3_prefix,
    )

    s3 = boto3.client("s3")
    paginator = s3.get_paginator("list_objects_v2")

    export_ids: set[str] = set()
    parquet_keys: list[str] = []

    for page in paginator.paginate(Bucket=request.s3_bucket, Prefix=s3_prefix):
        for obj in page.get("Contents", []):
            key = obj["Key"]
            match = re.search(r"(id-exp-[a-f0-9-]+)/", key)
            if match:
                export_ids.add(match.group(1))
            if key.endswith(".parquet") and "identifiers.identifiers/" in key:
                parquet_keys.append(key)

    if len(export_ids) == 0:
        raise RuntimeError(
            f"No exports found under s3://{request.s3_bucket}/{s3_prefix}"
        )
    if len(export_ids) > 1:
        raise RuntimeError(
            f"Multiple exports found for {request.export_date}: {sorted(export_ids)}. "
            "Cannot determine which export to use."
        )

    logger.info(
        "Found export",
        export_id=next(iter(export_ids)),
        parquet_file_count=len(parquet_keys),
    )

    parquet_dir = dest_dir / "parquet"
    parquet_dir.mkdir(parents=True, exist_ok=True)

    for key in parquet_keys:
        filename = Path(key).name
        local_path = parquet_dir / filename
        logger.info("Downloading", key=key, local_path=str(local_path))
        s3.download_file(request.s3_bucket, key, str(local_path))

    logger.info("Download complete", file_count=len(parquet_keys))
    return parquet_dir


def verify_migration(
    conn: pymysql.connections.Connection,
) -> tuple[int, int, int]:
    """Run post-migration integrity checks.

    Returns (canonical_count, identifiers_count, orphaned_count).
    """
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM canonical_ids")
    (canonical_count,) = cursor.fetchone()

    cursor.execute("SELECT COUNT(*) FROM identifiers")
    (identifiers_count,) = cursor.fetchone()

    cursor.execute(
        "SELECT COUNT(*) FROM identifiers i "
        "LEFT JOIN canonical_ids c ON i.CanonicalId = c.CanonicalId "
        "WHERE c.CanonicalId IS NULL"
    )
    (orphaned_count,) = cursor.fetchone()

    if orphaned_count > 0:
        raise RuntimeError(
            f"Migration verification failed: {orphaned_count} orphaned identifiers found"
        )

    logger.info(
        "Verification passed",
        canonical_ids=canonical_count,
        identifiers=identifiers_count,
        orphaned=orphaned_count,
    )
    return canonical_count, identifiers_count, orphaned_count


def handler(
    event: MigrationRequest,
    execution_context: ExecutionContext | None = None,
    config: IdMinterConfig | None = None,
) -> MigrationResponse:
    setup_logging(execution_context)
    cfg = config or ID_MINTER_CONFIG

    logger.info(
        "Migration starting",
        export_date=event.export_date,
        cluster_name=event.cluster_name,
        s3_bucket=event.s3_bucket,
        truncate=event.truncate,
        batch_size=event.batch_size,
    )

    apply_migrations(cfg)

    work_dir = Path(tempfile.mkdtemp(prefix="id_minter_migration_"))
    try:
        parquet_dir = download_parquet_from_s3(event, work_dir)

        conn = pymysql.connect(
            host=cfg.rds_client.primary_host,
            port=cfg.rds_client.port,
            user=cfg.rds_client.username,
            password=cfg.rds_client.password,
            database=cfg.identifiers_table.database,
            autocommit=False,
            local_infile=True,
        )
        try:
            result = migrate_from_parquet(
                conn,
                parquet_dir,
                batch_size=event.batch_size,
                truncate=event.truncate,
            )

            canonical_count, identifiers_count, orphaned_count = verify_migration(conn)
        finally:
            conn.close()
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)

    response = MigrationResponse(
        total_source_rows=result.total_source_rows,
        canonical_ids_inserted=result.canonical_ids_inserted,
        identifiers_inserted=result.identifiers_inserted,
        canonical_ids_verified=canonical_count,
        identifiers_verified=identifiers_count,
        orphaned_identifiers=orphaned_count,
    )
    logger.info("Migration complete", **response.model_dump())
    return response


def event_validator(raw_input: str) -> MigrationRequest:
    event = json.loads(raw_input)
    return MigrationRequest(**event)


def ecs_handler(arg_parser: argparse.ArgumentParser) -> None:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="id_minter_migration",
    )

    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
        execution_context=execution_context,
    )

    logger.info("ECS migration task completed")


def local_handler(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--export-date",
        type=str,
        required=True,
        help="Date of the S3 export (e.g. 2026-02-26).",
    )
    parser.add_argument(
        "--cluster-name",
        type=str,
        default="identifiers-serverless",
        help="RDS cluster name in the S3 export path.",
    )
    parser.add_argument(
        "--s3-bucket",
        type=str,
        default="wellcomecollection-platform-id-minter",
        help="S3 bucket containing the exports.",
    )
    parser.add_argument(
        "--truncate",
        action="store_true",
        default=False,
        help="Truncate target tables before migrating.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=1_000_000,
        help="Rows per LOAD DATA batch.",
    )
    parser.add_argument(
        "--rds-host",
        type=str,
        required=False,
        help="Override RDS host (defaults to env/config).",
    )
    parser.add_argument(
        "--rds-password",
        type=str,
        required=False,
        help="Override RDS password (defaults to env/config).",
    )

    args = parser.parse_args()

    request = MigrationRequest(
        export_date=args.export_date,
        cluster_name=args.cluster_name,
        s3_bucket=args.s3_bucket,
        truncate=args.truncate,
        batch_size=args.batch_size,
    )

    overrides: dict[str, Any] = {}
    if args.rds_host:
        overrides["rds_client"] = {"primary_host": args.rds_host}
    if args.rds_password:
        rds_overrides = overrides.get("rds_client", {})
        rds_overrides["password"] = args.rds_password
        overrides["rds_client"] = rds_overrides

    config_obj = IdMinterConfig(**overrides) if overrides else None

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="id_minter_migration",
    )

    response = handler(request, execution_context=execution_context, config=config_obj)
    logger.info(
        "Migration run complete",
        response=response.model_dump(),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Migrate identifiers from S3 parquet export to the new schema."
    )
    parser.add_argument(
        "--use-ecs",
        action="store_true",
        help="Run in ECS mode (expects --event and optional --task-token).",
    )
    args, _ = parser.parse_known_args()

    if args.use_ecs:
        ecs_handler(argparse.ArgumentParser(description="ECS migration handler."))
    else:
        local_handler(argparse.ArgumentParser(description="Run migration locally."))
