"""Axiell to Folio sync step.

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
  MANIFEST_S3_BUCKET     — S3 bucket name for JSON run reports
  AWS_REGION             — e.g. eu-west-1 (set automatically in Lambda)
  DRY_RUN                — default "true"; event.dry_run overrides

For local runs, OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME / OKAPI_PASSWORD
override the corresponding SSM fields (and skip SSM if all are set).
"""

from __future__ import annotations

import argparse
import json
import os
from typing import Any

import structlog

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.steps.axiell_folio_sync.models import (
    AxiellFolioSyncEvent,
    AxiellFolioSyncResponse,
)
from adapters.steps.axiell_folio_sync.ref_cache import RefCache
from adapters.steps.axiell_folio_sync.sync_to_folio import load_okapi_config, run_sync
from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.utils.adapter_store import AdapterStore
from clients.folio_client import FolioClient, FolioInventoryClient, ssl_context_from_env
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import ecs_handler

logger = structlog.get_logger(__name__)

PIPELINE_STEP = "axiell_folio_sync"


# ── adapter store read ────────────────────────────────────────────────────────


def _read_rows(
    changeset_ids: list[str] | None,
    sample_limit: int | None,
    *,
    use_rest_api_table: bool,
) -> list[dict]:
    """Read changed Axiell adapter rows via :class:`AdapterStoreSource`.

    Uses the same ``AXIELL_CONFIG`` as the Axiell adapter, so it works against
    S3 Tables (``use_rest_api_table=True``, production) or the local sqlite
    catalog (``use_rest_api_table=False``, local dev) with no code changes.
    Read-only: the table is never created.
    """
    table = AXIELL_CONFIG.build_adapter_table(
        use_rest_api_table=use_rest_api_table, create_if_not_exists=False
    )
    store = AdapterStore(table, namespace=AXIELL_CONFIG.config.adapter_namespace)
    source = AdapterStoreSource(store, changeset_ids=changeset_ids or [])

    if changeset_ids:
        logger.info("adapter_read", mode="changesets", changeset_ids=changeset_ids)
        return list(source.stream_raw())

    # Dev/smoke-test fallback: a sample of active records (no changesets given).
    limit = sample_limit or 10
    logger.info("adapter_read", mode="sample", limit=limit)
    rows: list[dict] = []
    for row in source.stream_raw():
        rows.append(row)
        if len(rows) >= limit:
            break
    return rows


# ── entry points ──────────────────────────────────────────────────────────────


def handler(
    event: AxiellFolioSyncEvent,
    *,
    use_rest_api_table: bool = True,
    execution_context: ExecutionContext | None = None,
) -> AxiellFolioSyncResponse:
    """Build the real dependencies (OKAPI client, ref cache, adapter rows) and
    hand them to :func:`run_sync`."""
    setup_logging(execution_context)

    env_dry_run = os.environ.get("DRY_RUN", "true").lower() not in ("false", "0", "no")
    dry_run = event.dry_run if event.dry_run is not None else env_dry_run
    manifest_bucket = os.environ.get("MANIFEST_S3_BUCKET")

    okapi = load_okapi_config()
    client = FolioClient(
        okapi["url"].rstrip("/"),
        okapi["tenant"],
        username=okapi["username"],
        password=okapi["password"],
        ssl_context=ssl_context_from_env(),
    )
    inventory = FolioInventoryClient(client)
    ref_cache = RefCache(inventory).load()

    rows = _read_rows(
        event.changeset_ids or None,
        event.sample_limit,
        use_rest_api_table=use_rest_api_table,
    )
    logger.info("adapter_read complete", rows=len(rows))

    return run_sync(
        event,
        rows,
        ref_cache,
        inventory,
        dry_run=dry_run,
        manifest_bucket=manifest_bucket,
    )


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
    parser = argparse.ArgumentParser(description="Run the Axiell to Folio sync step")
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
