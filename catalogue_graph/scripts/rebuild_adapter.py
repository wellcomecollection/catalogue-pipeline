"""Rebuild all Iceberg stores for an OAI-PMH adapter from a full snapshot.

Downloads every record from the adapter's OAI-PMH endpoint (and, for FOLIO,
the mod-inventory-storage items API), wipes the relevant stores, and reloads
from the snapshots. Optionally resets the harvest window cursor and publishes
the resulting changeset to downstream consumers (transformers).

If the snapshot file already exists at --snapshot-path, the download is skipped
and the existing snapshot is reused. Similarly for --folio-items-snapshot-path.

Usage:
    uv run python scripts/rebuild_adapter.py --adapter-type axiell --snapshot-path /tmp/axiell.parquet
    uv run python scripts/rebuild_adapter.py --adapter-type folio --use-rest-api-table --snapshot-path /tmp/folio.parquet --folio-items-snapshot-path /tmp/folio_items.parquet
"""

from __future__ import annotations

import argparse
import itertools
import json
import os
from collections.abc import Iterator
from datetime import UTC, datetime, timedelta
from typing import NamedTuple

import boto3
import pyarrow as pa
import pyarrow.parquet as pq
import structlog
from oai_pmh_client.client import OAIClient
from pyiceberg.expressions import EqualTo
from pyiceberg.table import ALWAYS_TRUE

from adapters.extractors.oai_pmh.folio.enrichment.enricher import fetch_item_rows
from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.extractors.oai_pmh.folio.enrichment.runtime import (
    build_inventory_client as build_folio_inventory_client,
)
from adapters.extractors.oai_pmh.folio.enrichment.runtime import (
    build_items_store,
)
from adapters.extractors.oai_pmh.record_writer import build_adapter_store_row
from adapters.extractors.oai_pmh.registry import AdapterType, get_config
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig
from adapters.steps.oai_pmh.reconcile import ReconcileEvent
from adapters.steps.oai_pmh.reconcile import build_runtime as build_reconcile_runtime
from adapters.steps.oai_pmh.reconcile import handler as reconcile_handler
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.argparse import add_adapter_event_args
from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from adapters.utils.window_summary import WindowSummary
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id

logger = structlog.get_logger(__name__)

BATCH_SIZE = 50_000
"""Number of rows per batch when writing to the snapshot file and loading
records into the adapter store."""

EVENT_BUS_NAME = "catalogue-pipeline-adapter-event-bus"


def _iter_snapshot_batches(snapshot_path: str) -> Iterator[pa.Table]:
    """Yield Arrow tables in batches from a parquet snapshot file."""
    reader = pq.ParquetFile(snapshot_path)
    for record_batch in reader.iter_batches(batch_size=BATCH_SIZE):
        yield pa.Table.from_batches([record_batch]).cast(ADAPTER_STORE_ARROW_SCHEMA)


def _download_to_snapshot(
    oai_client: OAIClient, config: OAIPMHAdapterConfig, snapshot_path: str
) -> int:
    """Stream records from the OAI-PMH endpoint to a parquet snapshot file in
    batches. Returns the total number of records downloaded."""
    total = 0
    records = oai_client.list_records(
        metadata_prefix=config.oai_metadata_prefix, set_spec=config.oai_set_spec
    )
    with pq.ParquetWriter(snapshot_path, ADAPTER_STORE_ARROW_SCHEMA) as writer:
        for batch in itertools.batched(records, BATCH_SIZE):
            rows = [
                build_adapter_store_row(
                    namespace=config.adapter_namespace,
                    identifier=record.header.identifier,
                    record=record,
                )
                for record in batch
            ]
            writer.write_table(
                pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
            )
            total += len(rows)
            logger.info("Download progress", total=total)
    logger.info("Download complete", path=snapshot_path, total=total)
    return total


def _download_items_to_snapshot(
    inventory_client: FolioInventoryClient,
    bib_snapshot_path: str,
    items_snapshot_path: str,
    namespace: str,
) -> int:
    """Fetch enriched FOLIO items for every bib instance and write them to a parquet
    snapshot file. Reads bib store IDs from the bib snapshot in batches, calls the
    mod-inventory-storage enrichedInstances API, and writes one row per instance.
    """
    total = 0
    with pq.ParquetWriter(items_snapshot_path, ADAPTER_STORE_ARROW_SCHEMA) as writer:
        for batch in _iter_snapshot_batches(bib_snapshot_path):
            store_ids: list[str] = [v for v in batch.column("id").to_pylist() if v]
            rows = fetch_item_rows(inventory_client, store_ids, namespace=namespace)
            if rows is None:
                continue
            writer.write_table(rows)
            total += rows.num_rows
            logger.info("Items download progress", total=total)
    logger.info("Items download complete", path=items_snapshot_path, total=total)
    return total


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


def _wipe_window_store(
    config: OAIPMHRuntimeConfig, *, use_rest_api_table: bool
) -> None:
    """Hard-delete all rows from the window status table."""
    window_store = config.build_window_store(use_rest_api_table=use_rest_api_table)
    row_count = window_store.table.scan().to_arrow().num_rows
    window_store.table.delete(ALWAYS_TRUE)
    logger.info("Window store wiped", row_count=row_count)


def _wipe_store(store: PipelineStore, store_name: str) -> None:
    current_snapshot = store.table.current_snapshot()
    if current_snapshot:
        logger.info(
            "Pre-wipe snapshot ID (use for Iceberg time-travel rollback if needed)",
            snapshot_id=current_snapshot.snapshot_id,
        )

    row_count = store.get_namespace_record_count()
    store.table.delete(EqualTo("namespace", store.namespace))
    logger.info("Store wiped", store_name=store_name, row_count=row_count)


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
        mappings_inserted=response.mappings_inserted,
        skipped=response.skipped,
    )


def _confirm_rebuild(adapter_type: AdapterType) -> None:
    """Confirmation gate before any stores are wiped."""
    confirm = input(
        f"WARNING: about to wipe all stores for '{adapter_type}' and rebuild "
        "from a full snapshot. Type CONFIRM to proceed: "
    ).strip()
    if confirm != "CONFIRM":
        raise SystemExit("Aborted.")


def _confirm_publish(adapter_type: AdapterType, changeset_count: int) -> None:
    """Confirm before publishing EventBridge events for each changeset."""
    confirm = input(
        f"About to publish {changeset_count} adapter.completed event(s) for "
        f"'{adapter_type}', triggering transformer runs. Type CONFIRM to proceed: "
    ).strip()
    if confirm != "CONFIRM":
        raise SystemExit("Aborted.")


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


def _populate_store_from_snapshot(store: AdapterStore, snapshot_path: str) -> list[str]:
    changeset_ids: list[str] = []
    for batch_num, batch in enumerate(_iter_snapshot_batches(snapshot_path), start=1):
        update = store.incremental_update(batch)
        if update:
            changeset_ids.append(update.changeset_id)
            logger.info(
                "Batch loaded",
                batch=batch_num,
                changeset_id=update.changeset_id,
                inserted=len(update.inserted_record_ids),
            )

    return changeset_ids


class _FolioItems(NamedTuple):
    """Bundles the items snapshot path and store so they can be treated as a single optional."""

    snapshot_path: str
    store: AdapterStore


def rebuild_adapter(
    adapter_type: AdapterType,
    *,
    use_rest_api_table: bool = False,
    snapshot_path: str,
    folio_items_snapshot_path: str | None = None,
    skip_publish_event: bool = False,
) -> None:
    config = get_config(adapter_type)
    job_id = create_job_id()

    _confirm_rebuild(adapter_type)

    # Phase 1: Window wipe + bib download.
    # The window store must be wiped before the download so the cursor is anchored at the rebuild start time.
    if not os.path.exists(snapshot_path):
        _wipe_window_store(config, use_rest_api_table=use_rest_api_table)
        _reset_window_cursor(
            config, datetime.now(UTC), use_rest_api_table=use_rest_api_table
        )

        oai_client = config.build_oai_client()
        total = _download_to_snapshot(oai_client, config.config, snapshot_path)
        if total == 0:
            raise RuntimeError(
                "OAI-PMH endpoint returned 0 records. This is almost certainly "
                "an error. Aborting to avoid wiping the adapter store."
            )
    else:
        logger.info(
            "Snapshot already exists. Resuming from existing file (window reset skipped).",
            path=snapshot_path,
        )

    # Phase 2: Items download (reads bib instance IDs from the bib snapshot).
    folio_items: _FolioItems | None = None
    if folio_items_snapshot_path is not None:
        folio_items = _FolioItems(
            snapshot_path=folio_items_snapshot_path,
            store=build_items_store(use_rest_api_table=use_rest_api_table),
        )

        if not os.path.exists(folio_items.snapshot_path):
            inventory_client = build_folio_inventory_client()
            _download_items_to_snapshot(
                inventory_client,
                bib_snapshot_path=snapshot_path,
                items_snapshot_path=folio_items.snapshot_path,
                namespace=folio_items.store.namespace,
            )
        else:
            logger.info(
                "Items snapshot already exists. Resuming from existing file.",
                path=folio_items.snapshot_path,
            )

    # Phase 3: Wipe and reload all stores from snapshots.
    adapter_store = config.build_adapter_store(use_rest_api_table=use_rest_api_table)
    _wipe_store(adapter_store, store_name="adapter store")
    changeset_ids = _populate_store_from_snapshot(adapter_store, snapshot_path)
    logger.info("All batches loaded", total_changesets=len(changeset_ids))

    if folio_items is not None:
        _wipe_store(folio_items.store, store_name="items store")
        _populate_store_from_snapshot(folio_items.store, folio_items.snapshot_path)

    if adapter_type == "axiell":
        reconcile_runtime = build_reconcile_runtime(
            adapter_type, use_rest_api_table=use_rest_api_table
        )
        _wipe_store(reconcile_runtime.reconciler_store, store_name="reconciler store")
        _run_reconcile(
            adapter_type,
            job_id,
            changeset_ids,
            use_rest_api_table=use_rest_api_table,
        )

    if not skip_publish_event:
        _confirm_publish(adapter_type, len(changeset_ids))
        for changeset_id in changeset_ids:
            _publish_adapter_event(adapter_type, job_id, [changeset_id])


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Rebuild an OAI-PMH adapter store from a full snapshot"
    )
    add_adapter_event_args(parser)
    parser.add_argument(
        "--snapshot-path",
        metavar="PATH",
        required=True,
        help="Path to the bib records snapshot file. If the file already exists, the download is skipped and the existing snapshot is used.",
    )
    parser.add_argument(
        "--folio-items-snapshot-path",
        metavar="PATH",
        help="(FOLIO only) Path to the items snapshot file. If provided, the FOLIO items store is also rebuilt. If the file already exists, the items download is skipped.",
    )
    parser.add_argument(
        "--skip-publish-event",
        action="store_true",
        help="Skip publishing the adapter.completed EventBridge event after the rebuild",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="rebuild_adapter")
    )

    if args.folio_items_snapshot_path is not None and args.adapter_type != "folio":
        raise ValueError(
            "folio_items_snapshot_path is only supported for adapter_type='folio'"
        )

    rebuild_adapter(
        args.adapter_type,
        use_rest_api_table=args.use_rest_api_table,
        snapshot_path=args.snapshot_path,
        folio_items_snapshot_path=args.folio_items_snapshot_path,
        skip_publish_event=args.skip_publish_event,
    )


if __name__ == "__main__":
    main()
