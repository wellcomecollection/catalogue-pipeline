"""Axiell → FOLIO sync step.

Triggered by ``axiell.adapter.completed`` EventBridge events. Reads changed
records from the Axiell Iceberg adapter table, maps them via MARCXML → FOLIO
Inventory payloads, and upserts (Instance → Holdings → Item) via OKAPI.

This is the FOLIO *outbound* (write) path and is distinct from the FOLIO adapter
enrichment step (``oai_pmh/folio_enrich.py``), which reads FOLIO into an internal
Iceberg store. The two share nothing beyond the name "folio".

Event shape (from EventBridge axiell.adapter.completed event):
  {
    "changeset_ids": ["id1", "id2"],
    "job_id": "adapter-job-xyz-123",
    "transformer_type": "axiell",
    "dry_run": true,
    "sample_limit": 50
  }

The adapter table is read via the shared ``AXIELL_CONFIG`` / ``AdapterStore``
(same as the Axiell adapter), which selects the S3 Tables catalog in the Lambda
and a local sqlite catalog for local runs — so no Iceberg-specific env vars.

Environment variables (injected by Terraform):
  OKAPI_SECRET_PARAM     — SSM path to {"url":…, "tenant":…, "username":…, "password":…}
  MANIFEST_S3_BUCKET     — S3 bucket name for NDJSON manifest storage
  AWS_REGION             — e.g. eu-west-1 (set automatically in Lambda)
  DRY_RUN                — default "true"; event.dry_run overrides

For local runs, OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME / OKAPI_PASSWORD
override the corresponding SSM fields (and skip SSM if all are set).
"""

from __future__ import annotations

import argparse
import json
import os
import urllib.parse
from datetime import UTC, datetime
from typing import Any, cast

import boto3
import structlog
from pydantic import BaseModel, Field

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.transformers.axiell_folio_sync.mapping import MappingError, build_payloads
from adapters.transformers.axiell_folio_sync.ref_cache import RefCache
from adapters.transformers.axiell_folio_sync.upsert import upsert_from_payloads
from adapters.utils.adapter_store import AdapterStore
from clients.folio_client import FolioClient, ssl_context_from_env
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import ecs_handler

logger = structlog.get_logger(__name__)

PIPELINE_STEP = "axiell_folio_sync"

# In-memory success buffer is flushed to S3 in chunks of this size.
BATCH_SIZE = 5000


class AxiellFolioSyncEvent(BaseModel):
    """Input to the sync step (the ``detail`` of an axiell.adapter.completed event)."""

    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    transformer_type: str | None = None
    # ``None`` means "fall back to the DRY_RUN env var" (default true).
    dry_run: bool | None = None
    # Dev/smoke-test only: cap records processed when no changeset_ids are given.
    sample_limit: int | None = None


class AxiellFolioSyncResponse(BaseModel):
    """Output of the sync step."""

    job_id: str
    dry_run: bool
    manifest_s3_path: str | None = None
    counts: dict[str, int] = Field(default_factory=dict)
    total_successful: int = 0
    total_errors: int = 0
    total_records: int = 0


def _utc_now_iso() -> str:
    """Timezone-aware UTC timestamp in ISO 8601 (datetime.utcnow() is deprecated)."""
    return datetime.now(UTC).isoformat()


# Lazy singletons — survive across warm Lambda invocations.
_ssm_client: Any = None
_s3_client: Any = None


def _ssm() -> Any:
    global _ssm_client
    if _ssm_client is None:
        _ssm_client = boto3.client("ssm", region_name=os.environ["AWS_REGION"])
    return _ssm_client


def _s3() -> Any:
    global _s3_client
    if _s3_client is None:
        _s3_client = boto3.client("s3", region_name=os.environ["AWS_REGION"])
    return _s3_client


# ── OKAPI auth ────────────────────────────────────────────────────────────────


def _load_okapi_config() -> dict[str, str]:
    """FOLIO OKAPI url/tenant/username/password from the SSM SecureString JSON.

    Per-field env overrides (OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME /
    OKAPI_PASSWORD) let local runs skip SSM entirely; in the Lambda all four come
    from the ``OKAPI_SECRET_PARAM`` parameter.
    """
    data: dict[str, str] = {}
    param_name = os.environ.get("OKAPI_SECRET_PARAM")
    if param_name:
        param = _ssm().get_parameter(Name=param_name, WithDecryption=True)
        data = json.loads(param["Parameter"]["Value"])
    return {
        "url": os.environ.get("OKAPI_URL") or data["url"],
        "tenant": os.environ.get("OKAPI_TENANT") or data["tenant"],
        "username": os.environ.get("OKAPI_USERNAME") or data["username"],
        "password": os.environ.get("OKAPI_PASSWORD") or data["password"],
    }


def _make_folio_callables(client: FolioClient) -> tuple[Any, Any, Any]:
    """Adapt a :class:`FolioClient` to the (folio_get, folio_post, folio_put)
    callables that ``ref_cache`` and ``upsert`` expect."""

    def folio_get(path: str, params: dict | None = None) -> dict:
        if params:
            path = f"{path}?{urllib.parse.urlencode(params)}"
        status, data = client.request("GET", path)
        if status >= 400:
            raise RuntimeError(f"GET {path} failed: {status} {str(data)[:200]}")
        return cast("dict", data)

    def folio_post(path: str, payload: dict) -> dict:
        status, data = client.request("POST", path, payload)
        if status not in (200, 201):
            raise RuntimeError(f"POST {path} failed: {status} {str(data)[:200]}")
        return cast("dict", data)

    def folio_put(path: str, payload: dict) -> dict:
        status, data = client.request("PUT", path, payload, accept="text/plain")
        if status >= 400:
            raise RuntimeError(f"PUT {path} failed: {status} {str(data)[:200]}")
        return cast("dict", data)

    return folio_get, folio_post, folio_put


# ── adapter store read ────────────────────────────────────────────────────────


def _read_rows(
    changeset_ids: list[str] | None,
    sample_limit: int | None,
    *,
    use_rest_api_table: bool,
) -> list[dict]:
    """Read changed Axiell adapter rows via the shared ``AdapterStore``.

    Uses the same ``AXIELL_CONFIG`` as the Axiell adapter, so it works against
    S3 Tables (``use_rest_api_table=True``, production) or the local sqlite
    catalog (``use_rest_api_table=False``, local dev) with no code changes.
    Read-only: the table is never created.
    """
    table = AXIELL_CONFIG.build_adapter_table(
        use_rest_api_table=use_rest_api_table, create_if_not_exists=False
    )
    store = AdapterStore(table, namespace=AXIELL_CONFIG.config.adapter_namespace)

    if changeset_ids:
        logger.info("adapter_read", mode="changesets", changeset_ids=changeset_ids)
        return store.get_records_by_changesets(changeset_ids).to_pylist()

    # Dev/smoke-test fallback: a sample of active records (no changesets given).
    limit = sample_limit or 10
    logger.info("adapter_read", mode="sample", limit=limit)
    return store.get_active_namespace_records().to_pylist()[:limit]


# ── CloudWatch metrics ────────────────────────────────────────────────────────


def _emit_metrics(counts: dict) -> None:
    boto3.client("cloudwatch", region_name=os.environ["AWS_REGION"]).put_metric_data(
        Namespace="AxiellFolioSync",
        MetricData=[
            {
                "MetricName": "RecordsCreated",
                "Value": counts.get("created", 0),
                "Unit": "Count",
            },
            {
                "MetricName": "RecordsUpdated",
                "Value": counts.get("updated", 0),
                "Unit": "Count",
            },
            {
                "MetricName": "RecordsSuppressed",
                "Value": counts.get("suppressed", 0),
                "Unit": "Count",
            },
            {
                "MetricName": "RecordsFailed",
                "Value": counts.get("failed", 0),
                "Unit": "Count",
            },
            {
                "MetricName": "RecordsProcessed",
                "Value": counts.get("total", 0),
                "Unit": "Count",
            },
        ],
    )


# ── S3 NDJSON manifest writing ────────────────────────────────────────────────


def _flush_success_batch(bucket: str, job_id: str, batch: list[dict]) -> None:
    if not batch:
        return
    key = f"manifests/{job_id}.ids.ndjson"
    ndjson_lines = "\n".join(json.dumps(record) for record in batch)
    _s3().put_object(
        Bucket=bucket,
        Key=key,
        Body=(ndjson_lines + "\n").encode("utf-8"),
        ContentType="application/x-ndjson",
    )
    logger.info("flushed_success_batch", job_id=job_id, key=key, count=len(batch))


def _write_error_manifest(bucket: str, job_id: str, errors: list[dict]) -> None:
    if not errors:
        return
    key = f"manifests/{job_id}.ids.failures.ndjson"
    ndjson_lines = "\n".join(json.dumps(e) for e in errors)
    _s3().put_object(
        Bucket=bucket,
        Key=key,
        Body=(ndjson_lines + "\n").encode("utf-8"),
        ContentType="application/x-ndjson",
    )
    logger.info("wrote_error_manifest", job_id=job_id, key=key, count=len(errors))


def _write_metadata_manifest(
    bucket: str,
    job_id: str,
    total_successful: int,
    total_errors: int,
    has_errors_file: bool,
    changeset_ids: list[str] | None = None,
) -> str:
    metadata: dict[str, Any] = {
        "jobId": job_id,
        "timestamp": _utc_now_iso(),
        "summary": {
            "totalSuccessful": total_successful,
            "totalErrors": total_errors,
            "totalRecords": total_successful + total_errors,
        },
        "files": {
            "success": f"s3://{bucket}/manifests/{job_id}.ids.ndjson",
            "errors": f"s3://{bucket}/manifests/{job_id}.ids.failures.ndjson"
            if has_errors_file
            else None,
        },
    }
    if changeset_ids:
        metadata["changesetIds"] = changeset_ids
    key = f"manifests/{job_id}.manifest.json"
    _s3().put_object(
        Bucket=bucket,
        Key=key,
        Body=json.dumps(metadata, indent=2).encode("utf-8"),
        ContentType="application/json",
    )
    logger.info("wrote_metadata_manifest", job_id=job_id, key=key)
    return f"s3://{bucket}/{key}"


# ── core ──────────────────────────────────────────────────────────────────────


def run_sync(
    event: AxiellFolioSyncEvent, *, use_rest_api_table: bool = True
) -> AxiellFolioSyncResponse:
    """Read the Axiell adapter table for the event's changesets and upsert to FOLIO."""
    env_dry_run = os.environ.get("DRY_RUN", "true").lower() not in ("false", "0", "no")
    dry_run = event.dry_run if event.dry_run is not None else env_dry_run

    manifest_bucket = os.environ.get("MANIFEST_S3_BUCKET")
    okapi = _load_okapi_config()

    logger.info(
        "axiell_folio_sync start",
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        dry_run=dry_run,
    )

    client = FolioClient(
        okapi["url"].rstrip("/"),
        okapi["tenant"],
        username=okapi["username"],
        password=okapi["password"],
        ssl_context=ssl_context_from_env(),
    )
    folio_get, folio_post, folio_put = _make_folio_callables(client)
    ref_cache = RefCache(folio_get).load()

    rows = _read_rows(
        event.changeset_ids or None,
        event.sample_limit,
        use_rest_api_table=use_rest_api_table,
    )
    logger.info("adapter_read complete", rows=len(rows))

    successful_batch: list[dict] = []
    errors_list: list[dict] = []
    total_successful = 0
    total_errors = 0
    counts: dict[str, int] = {
        "created": 0,
        "updated": 0,
        "suppressed": 0,
        "failed": 0,
        "total": 0,
    }

    for row in rows:
        source_id: str = row.get("id", "unknown")
        changeset_id: str = row.get("changeset", "unknown")
        counts["total"] += 1

        if not row.get("content"):
            counts["failed"] += 1
            total_errors += 1
            logger.warning("empty_content", source_id=source_id)
            errors_list.append(
                {
                    "jobId": event.job_id,
                    "sourceId": source_id,
                    "changesetId": changeset_id,
                    "stage": "scan",
                    "error": "empty_content",
                    "timestamp": _utc_now_iso(),
                }
            )
            continue

        try:
            mapped = build_payloads(
                row["content"], ref_cache, deleted=bool(row.get("deleted"))
            )
        except MappingError as exc:
            counts["failed"] += 1
            total_errors += 1
            logger.warning("mapping_error", source_id=source_id, error=str(exc))
            errors_list.append(
                {
                    "jobId": event.job_id,
                    "sourceId": source_id,
                    "changesetId": changeset_id,
                    "stage": "mapping",
                    "error": str(exc),
                    "timestamp": _utc_now_iso(),
                }
            )
            continue

        result = upsert_from_payloads(
            mapped,
            folio_get=folio_get,
            folio_post=folio_post,
            folio_put=folio_put,
            dry_run=dry_run,
        )

        if result["errors"]:
            counts["failed"] += 1
            total_errors += 1
            errors_list.append(
                {
                    "jobId": event.job_id,
                    "sourceId": source_id,
                    "changesetId": changeset_id,
                    "stage": "upsert",
                    "errors": result["errors"],
                    "timestamp": _utc_now_iso(),
                }
            )
        else:
            total_successful += 1
            for entity in ("instance", "holdings", "item"):
                action = result[entity]["action"]
                if action == "create":
                    counts["created"] += 1
                elif action == "update":
                    counts["updated"] += 1
                elif action == "suppress":
                    counts["suppressed"] += 1

            successful_batch.append(
                {
                    "jobId": event.job_id,
                    "sourceId": source_id,
                    "changesetId": changeset_id,
                    "instanceAction": result["instance"]["action"],
                    "holdingsAction": result["holdings"]["action"],
                    "itemAction": result["item"]["action"],
                    "timestamp": _utc_now_iso(),
                }
            )

            if len(successful_batch) >= BATCH_SIZE and manifest_bucket:
                _flush_success_batch(manifest_bucket, event.job_id, successful_batch)
                successful_batch = []

        logger.info(
            "upsert_result",
            source_id=source_id,
            instance=result.get("instance", {}).get("action", "skip"),
            holdings=result.get("holdings", {}).get("action", "skip"),
            item=result.get("item", {}).get("action", "skip"),
            errors=len(result.get("errors", [])),
        )

    if successful_batch and manifest_bucket:
        _flush_success_batch(manifest_bucket, event.job_id, successful_batch)

    manifest_path: str | None = None
    if manifest_bucket:
        if errors_list:
            _write_error_manifest(manifest_bucket, event.job_id, errors_list)
        manifest_path = _write_metadata_manifest(
            manifest_bucket,
            event.job_id,
            total_successful,
            total_errors,
            has_errors_file=bool(errors_list),
            changeset_ids=event.changeset_ids or None,
        )

    if not dry_run:
        _emit_metrics(counts)

    logger.info(
        "axiell_folio_sync complete",
        job_id=event.job_id,
        total=counts["total"],
        successful=total_successful,
        errors=total_errors,
        dry_run=dry_run,
    )

    return AxiellFolioSyncResponse(
        job_id=event.job_id,
        dry_run=dry_run,
        manifest_s3_path=manifest_path,
        counts=counts,
        total_successful=total_successful,
        total_errors=total_errors,
        total_records=total_successful + total_errors,
    )


# ── entry points ──────────────────────────────────────────────────────────────


def handler(
    event: AxiellFolioSyncEvent,
    *,
    use_rest_api_table: bool = True,
    execution_context: ExecutionContext | None = None,
) -> AxiellFolioSyncResponse:
    setup_logging(execution_context)
    return run_sync(event, use_rest_api_table=use_rest_api_table)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=PIPELINE_STEP,
    )
    return handler(
        AxiellFolioSyncEvent.model_validate(event),
        use_rest_api_table=True,
        execution_context=execution_context,
    ).model_dump(mode="json")


def ecs_task_handler(
    event: AxiellFolioSyncEvent,
    execution_context: ExecutionContext,
) -> AxiellFolioSyncResponse:
    """ECS task handler invoked by the ecs_handler utility (waitForTaskToken)."""
    return handler(event, use_rest_api_table=True, execution_context=execution_context)


def event_validator(raw_input: str) -> AxiellFolioSyncEvent:
    return AxiellFolioSyncEvent.model_validate(json.loads(raw_input))


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the sync step from the command line for development / smoke-testing.

    Dry-run by default; pass --live to write to FOLIO.
    """
    parser.add_argument("--job-id", required=True, help="Unique job identifier")
    parser.add_argument(
        "--changeset-ids",
        nargs="+",
        metavar="ID",
        help="Specific changeset IDs to process (overrides --sample-limit)",
    )
    parser.add_argument(
        "--sample-limit",
        type=int,
        default=5,
        help="Max records to read when no changeset ids are given",
    )
    parser.add_argument(
        "--live", action="store_true", help="Disable dry-run and write to FOLIO"
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Read from the S3 Tables catalog instead of the local sqlite catalog",
    )
    args = parser.parse_args()

    event = AxiellFolioSyncEvent(
        job_id=args.job_id,
        changeset_ids=args.changeset_ids or [],
        sample_limit=None if args.changeset_ids else args.sample_limit,
        dry_run=not args.live,
    )
    response = handler(event, use_rest_api_table=args.use_rest_api_table)
    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the Axiell → FOLIO sync step")
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Invoke the local CLI handler instead of the ECS handler.",
    )
    cli_args, _ = parser.parse_known_args()

    if cli_args.use_cli:
        local_handler(parser)
    else:
        ecs_handler(
            arg_parser=parser,
            handler=ecs_task_handler,
            event_validator=event_validator,
            pipeline_step=PIPELINE_STEP,
        )
