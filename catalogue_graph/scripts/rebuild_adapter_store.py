"""Rebuild an OAI-PMH adapter store from a full snapshot.

Downloads every record from the adapter's OAI-PMH endpoint and calls
snapshot_sync on the AdapterStore, applying inserts, updates, and soft-deletes
in one shot. Optionally resets the harvest window cursor and publishes the
resulting changeset to downstream consumers (transformers).

Usage (from catalogue_graph/):

    uv run python scripts/rebuild_adapter_store.py --adapter-type axiell
    uv run python scripts/rebuild_adapter_store.py --adapter-type folio --use-rest-api-table
    uv run python scripts/rebuild_adapter_store.py --adapter-type axiell --publish-event
"""

from __future__ import annotations

import argparse
import json
import uuid
from datetime import UTC, datetime, timedelta
from typing import Any

import boto3
import polars as pl
import pyarrow as pa
import structlog
from adapters.extractors.oai_pmh.record_writer import _serialize_metadata
from adapters.extractors.oai_pmh.registry import AdapterType, get_config
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig
from adapters.steps.oai_pmh.reconcile import ReconcileEvent
from adapters.steps.oai_pmh.reconcile import build_runtime as build_reconcile_runtime
from adapters.steps.oai_pmh.reconcile import handler as reconcile_handler
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.argparse import add_adapter_event_args
from adapters.utils.pipeline_store import PipelineStoreUpdate
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from adapters.utils.window_summary import WindowSummary
from oai_pmh_client.client import OAIClient
from pyiceberg.expressions import EqualTo
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

EVENT_BUS_NAME = "catalogue-pipeline-adapter-event-bus"


def save_snapshot(rows: list[dict[str, Any]], path: str) -> None:
    pl.DataFrame(rows).write_parquet(path)
    logger.info("Snapshot saved", path=path, row_count=len(rows))


def load_snapshot(path: str) -> list[dict[str, Any]]:
    rows = pl.read_parquet(path).to_dicts()
    logger.info("Snapshot loaded", path=path, row_count=len(rows))
    return rows


def _wipe_adapter_table(adapter_store: AdapterStore) -> None:
    namespace = adapter_store.namespace

    ids = adapter_store.table.scan(
        row_filter=EqualTo("namespace", namespace), selected_fields=("id",)
    )
    row_count = ids.to_arrow().num_rows

    confirm = input(
        f"WARNING: this will hard-delete ALL {row_count:,} rows for namespace '{namespace}' "
        "from the adapter table.\nType CONFIRM to proceed: "
    ).strip()
    if confirm != "CONFIRM":
        raise SystemExit("Aborted: wipe not confirmed.")

    adapter_store.table.delete(EqualTo("namespace", namespace))
    logger.info("Table wiped", namespace=namespace)


def _get_oai_rows(oai_client: OAIClient, config: OAIPMHAdapterConfig) -> list[dict]:
    rows: list[dict[str, Any]] = []
    for record in oai_client.list_records(
        metadata_prefix=config.oai_metadata_prefix, set_spec=config.oai_set_spec
    ):
        content = _serialize_metadata(record)
        rows.append(
            {
                "namespace": config.adapter_namespace,
                "id": record.header.identifier,
                "content": content,
                "last_modified": record.header.datestamp,
                "deleted": content is None,
            }
        )

        if len(rows) % 10_000 == 0:
            logger.info("Download progress", total=len(rows))

    logger.info("Download complete", total=len(rows))

    return rows


def _snapshot_sync(
    rows: list[dict[str, Any]], adapter_store: AdapterStore
) -> PipelineStoreUpdate | None:
    table = pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)

    logger.info("Running snapshot sync", row_count=len(rows))
    update = adapter_store.snapshot_sync(table)

    if update:
        logger.info(
            "Snapshot sync complete",
            changeset_id=update.changeset_id,
            inserted=len(update.inserted_record_ids),
            updated=len(update.updated_record_ids),
        )
    else:
        logger.info("Snapshot sync produced no changes (table already up to date)")

    return update


def _run_reconcile(
    adapter_type: str,
    job_id: str,
    changeset_ids: list[str],
    *,
    use_rest_api_table: bool,
) -> None:
    event = ReconcileEvent(
        job_id=job_id,
        adapter_type=adapter_type,
        changeset_ids=changeset_ids,
    )
    runtime = build_reconcile_runtime(
        adapter_type, use_rest_api_table=use_rest_api_table
    )
    response = reconcile_handler(event, runtime)
    logger.info(
        "Reconcile complete",
        facts_written=response.facts_written,
        mappings_inserted=response.mappings_inserted,
        mappings_updated=response.mappings_updated,
        skipped=response.skipped,
    )


def _reset_window_cursor(
    config: OAIPMHRuntimeConfig, now: datetime, *, use_rest_api_table: bool
) -> None:
    """Write a synthetic published window row to advance the trigger cursor to now."""
    window_store = config.build_window_store(use_rest_api_table=use_rest_api_table)
    synthetic = WindowSummary(
        window_start=now - timedelta(minutes=1),
        window_end=now,
        state="success",
        attempts=1,
        record_ids=[],
        last_error=None,
        updated_at=now,
        tags={"published_at": now.isoformat()},
    )
    window_store.upsert(synthetic)
    logger.info("Window cursor reset", published_at=now.isoformat())


def _publish_adapter_event(
    adapter_type: str, job_id: str, changeset_ids: list[str]
) -> None:
    client = boto3.client("events")
    response = client.put_events(
        Entries=[
            {
                "Source": f"{adapter_type}.adapter",
                "DetailType": f"{adapter_type}.adapter.completed",
                "Detail": json.dumps(
                    {
                        "transformer_type": adapter_type,
                        "job_id": job_id,
                        "changeset_ids": changeset_ids,
                    }
                ),
                "EventBusName": EVENT_BUS_NAME,
            }
        ]
    )
    if response["FailedEntryCount"] > 0:
        raise RuntimeError(f"Failed to publish EventBridge event: {response}")
    logger.info(
        "Published adapter event",
        adapter_type=adapter_type,
        job_id=job_id,
        changeset_ids=changeset_ids,
    )


def rebuild_adapter_store(
    adapter_type: AdapterType,
    *,
    use_rest_api_table: bool = False,
    data_source: str = "download",
    snapshot_path: str,
    wipe_table: bool = False,
    publish_event: bool = False,
) -> None:
    config = get_config(adapter_type)
    now = datetime.now(UTC)
    job_id = str(uuid.uuid4())

    _reset_window_cursor(config, now, use_rest_api_table=use_rest_api_table)

    if data_source == "snapshot":
        rows = load_snapshot(snapshot_path)
    else:
        oai_client = config.build_oai_client()
        rows = _get_oai_rows(oai_client, config.config)
        if not rows:
            logger.info("No records returned from OAI-PMH endpoint. Nothing to sync.")
            return

        save_snapshot(rows, snapshot_path)

    adapter_store = config.build_adapter_store(use_rest_api_table=use_rest_api_table)
    if wipe_table:
        _wipe_adapter_table(adapter_store)

    update = _snapshot_sync(rows, adapter_store)

    if not update:
        logger.info("No changes; skipping reconcile and event publish.")
        return

    changeset_ids = [update.changeset_id]

    if adapter_type == "axiell":
        _run_reconcile(
            adapter_type,
            job_id,
            changeset_ids,
            use_rest_api_table=use_rest_api_table,
        )

    if publish_event:
        _publish_adapter_event(adapter_type, job_id, changeset_ids)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Rebuild an OAI-PMH adapter store from a full snapshot"
    )
    add_adapter_event_args(parser)
    parser.add_argument(
        "--data-source",
        choices=["download", "snapshot"],
        default="download",
        help="Download fresh records from the OAI-PMH endpoint, or load from a snapshot file (default: download)",
    )
    parser.add_argument(
        "--snapshot-path",
        metavar="PATH",
        help="When --data-source=snapshot: path to load from. When --data-source=download: path to save a snapshot to (optional).",
    )
    parser.add_argument(
        "--wipe-table",
        action="store_true",
        help="Hard-delete all existing rows for this adapter's namespace before loading (prompts for confirmation)",
    )
    parser.add_argument(
        "--publish-event",
        action="store_true",
        help="Publish an adapter.completed EventBridge event after the rebuild, triggering downstream transformers",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="rebuild_adapter_store")
    )

    rebuild_adapter_store(
        args.adapter_type,
        use_rest_api_table=args.use_rest_api_table,
        data_source=args.data_source,
        snapshot_path=args.snapshot_path,
        wipe_table=args.wipe_table,
        publish_event=args.publish_event,
    )


if __name__ == "__main__":
    main()
