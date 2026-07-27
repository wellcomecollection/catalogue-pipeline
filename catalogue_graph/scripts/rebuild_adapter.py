"""Rebuild all Iceberg stores for an OAI-PMH adapter from a full snapshot.

Downloads every record from the adapter's OAI-PMH endpoint (and, for FOLIO,
the mod-inventory-storage items API), wipes the relevant stores, and reloads
from the snapshots. Resets the harvest window cursor and publishes the
resulting changesets to downstream consumers (transformers).

The rebuild emits no downstream deletions: run it against a clean pipeline,
or wipe the pipeline's downstream state first.

If the snapshot file already exists at --snapshot-path, the download is skipped
and the existing snapshot is reused. Similarly for --folio-items-snapshot-path.
Snapshot files are only ever moved into place after a complete, successful
write, so an existing file is always safe to resume from.

Usage:
    uv run python scripts/rebuild_adapter.py --adapter-type axiell --use-rest-api-table --snapshot-path /tmp/axiell.parquet
    uv run python scripts/rebuild_adapter.py --adapter-type folio --use-rest-api-table --snapshot-path /tmp/folio.parquet --folio-items-snapshot-path /tmp/folio_items.parquet
"""

from __future__ import annotations

import argparse
import json
import os
import time
from collections.abc import Iterator
from datetime import UTC, datetime, timedelta
from typing import NamedTuple

import boto3
import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.parquet as pq
import structlog
from oai_pmh_client.client import OAIClient
from oai_pmh_client.models import NS, Record, ResumptionToken
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
from adapters.steps.oai_pmh.reconcile import ReconcileEvent, ReconcileRuntime
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

PROGRESS_LOG_EVERY = 5_000
"""Records between download progress log lines."""

EVENT_BUS_NAME = "catalogue-pipeline-adapter-event-bus"


def _iter_snapshot_batches(snapshot_path: str) -> Iterator[pa.Table]:
    """Yield Arrow tables in batches from a parquet snapshot file."""
    reader = pq.ParquetFile(snapshot_path)
    for record_batch in reader.iter_batches(batch_size=BATCH_SIZE):
        yield pa.Table.from_batches([record_batch]).cast(ADAPTER_STORE_ARROW_SCHEMA)


def _iter_record_pages(
    oai_client: OAIClient, config: OAIPMHAdapterConfig
) -> Iterator[tuple[list[Record], int | None]]:
    """Yield ListRecords pages with the record total the server reports.

    `OAIClient.list_records` keeps the resumption token inside its own loop and
    yields a flat record stream, which leaves a caller unable to see the
    completeListSize the token carries. Driving the request loop here exposes
    it, so a download can report how far through it is. The paging behaviour
    matches the client's, and this belongs in the oai-pmh-client package once
    it grows a public API for it.
    """
    params: dict[str, str | None] = {
        "metadataPrefix": config.oai_metadata_prefix,
        "set": config.oai_set_spec,
    }

    while True:
        xml = oai_client._request("ListRecords", **params)
        records = [
            Record.from_xml(element)
            for element in xml.findall("./oai:ListRecords/oai:record", namespaces=NS)
        ]

        token_element = xml.find(".//oai:resumptionToken", namespaces=NS)
        token = (
            ResumptionToken.from_xml(token_element)
            if token_element is not None and token_element.text
            else None
        )

        yield records, token.complete_list_size if token else None

        if token is None:
            return
        # When using a resumption token, the original parameters must be omitted.
        params = {"resumptionToken": token.value}


def _log_download_progress(total: int, expected: int | None, started_at: float) -> None:
    """Log harvest position, rate and estimated time remaining."""
    elapsed = max(time.time() - started_at, 1e-6)
    rate = total / elapsed
    remaining = (expected - total) / rate if expected and expected > total else None
    logger.info(
        "Download progress",
        total=total,
        expected=expected,
        percent=round(100 * total / expected, 1) if expected else None,
        records_per_second=round(rate, 1),
        eta_minutes=round(remaining / 60) if remaining else None,
    )


def _download_to_snapshot(
    oai_client: OAIClient, config: OAIPMHAdapterConfig, snapshot_path: str
) -> int:
    """Stream records from the OAI-PMH endpoint to a parquet snapshot file in
    batches. Returns the total number of records downloaded.

    Writes to a `.partial` file moved into place only on success, so an
    interrupted download cannot leave a truncated snapshot for a resumed run
    to trust. An interrupted download has to start again: this harvest runs for
    hours, so run it somewhere it will not be disturbed.
    """
    total = 0
    expected: int | None = None
    logged_at = 0
    started_at = time.time()
    rows: list[dict] = []
    partial_path = f"{snapshot_path}.partial"

    with pq.ParquetWriter(partial_path, ADAPTER_STORE_ARROW_SCHEMA) as writer:

        def flush() -> None:
            writer.write_table(
                pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
            )
            rows.clear()

        for records, complete_list_size in _iter_record_pages(oai_client, config):
            expected = complete_list_size or expected
            rows.extend(
                build_adapter_store_row(
                    namespace=config.adapter_namespace,
                    identifier=record.header.identifier,
                    record=record,
                )
                for record in records
            )
            total += len(records)

            if total - logged_at >= PROGRESS_LOG_EVERY:
                _log_download_progress(total, expected, started_at)
                logged_at = total
            if len(rows) >= BATCH_SIZE:
                flush()

        if rows:
            flush()

    if total == 0:
        os.remove(partial_path)
        raise RuntimeError(
            "OAI-PMH endpoint returned 0 records. This is almost certainly "
            "an error. Aborting to avoid wiping the adapter store."
        )

    if expected is not None and total != expected:
        logger.warning(
            "Downloaded record count does not match the count the server "
            "reported. The rebuild replaces the store with this snapshot, so "
            "check the difference before continuing.",
            total=total,
            expected=expected,
            difference=total - expected,
        )

    os.replace(partial_path, snapshot_path)
    logger.info("Download complete", path=snapshot_path, total=total, expected=expected)
    return total


def _download_items_to_snapshot(
    inventory_client: FolioInventoryClient,
    bib_snapshot_path: str,
    items_snapshot_path: str,
    namespace: str,
) -> int:
    """Fetch enriched FOLIO items for every active bib and write them to a parquet
    snapshot. Reads bib ids from the bib snapshot in batches, skipping
    tombstoned bibs, and writes one row per instance. Atomic, like the bib
    snapshot.
    """
    total = 0
    reader = pq.ParquetFile(bib_snapshot_path)
    partial_path = f"{items_snapshot_path}.partial"
    with pq.ParquetWriter(partial_path, ADAPTER_STORE_ARROW_SCHEMA) as writer:
        for record_batch in reader.iter_batches(
            batch_size=BATCH_SIZE, columns=["id", "deleted"]
        ):
            batch = pa.Table.from_batches([record_batch])
            not_deleted = pc.field("deleted").is_null() | (
                pc.field("deleted") == False  # noqa: E712
            )
            active = batch.filter(not_deleted)
            store_ids: list[str] = [v for v in active.column("id").to_pylist() if v]
            if not store_ids:
                continue
            rows = fetch_item_rows(inventory_client, store_ids, namespace=namespace)
            if rows is None:
                continue
            writer.write_table(rows)
            total += rows.num_rows
            logger.info("Items download progress", total=total)

    if total == 0:
        os.remove(partial_path)
        raise RuntimeError(
            "FOLIO items download returned 0 items across all bibs. This is "
            "almost certainly an error (e.g. the enrichment endpoint returning "
            "empty responses). Aborting to avoid wiping the items store."
        )
    os.replace(partial_path, items_snapshot_path)
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
    row_count = (
        window_store.table.scan(selected_fields=("window_key",)).to_arrow().num_rows
    )
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
    runtime: ReconcileRuntime,
    adapter_type: str,
    job_id: str,
    changeset_ids: list[str],
) -> None:
    """Run the reconcile step once per changeset.

    One call over all of them would materialise the whole store as dicts. Each
    record id appears in one changeset and the baseline was just wiped, so the
    result is the same.
    """
    total_inserted = 0
    for changeset_id in changeset_ids:
        event = ReconcileEvent(
            job_id=job_id,
            adapter_type=adapter_type,
            changeset_ids=[changeset_id],
        )
        response = reconcile_handler(event, runtime)
        total_inserted += response.mappings_inserted
        logger.info(
            "Reconciled changeset",
            changeset_id=changeset_id,
            mappings_inserted=response.mappings_inserted,
            skipped=response.skipped,
        )
    logger.info(
        "Reconcile complete",
        changesets=len(changeset_ids),
        mappings_inserted=total_inserted,
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
        f"'{adapter_type}', triggering transformer runs in every pipeline stack "
        "with an enabled adapter trigger (and, for Axiell, the Axiell->FOLIO "
        "sync unless its rule is disabled). Type CONFIRM to proceed: "
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
    publish_interval_seconds: float = 0.0,
) -> None:
    if not use_rest_api_table and not skip_publish_event:
        raise ValueError(
            "--skip-publish-event is required without --use-rest-api-table: "
            "a local-table rebuild would still publish real adapter.completed "
            "events, triggering production transformer runs against changeset "
            "ids that only exist locally."
        )

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
        _download_to_snapshot(oai_client, config.config, snapshot_path)

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

    adapter_store = config.build_adapter_store(use_rest_api_table=use_rest_api_table)

    # Phase 3: Wipe and reload all stores from snapshots.
    _wipe_store(adapter_store, store_name="adapter store")
    changeset_ids = _populate_store_from_snapshot(adapter_store, snapshot_path)
    logger.info("All batches loaded", total_changesets=len(changeset_ids))

    if folio_items is not None:
        _wipe_store(folio_items.store, store_name="items store")
        _populate_store_from_snapshot(folio_items.store, folio_items.snapshot_path)

    if adapter_type == "axiell":
        # Build the reconcile runtime after the load: a pyiceberg handle is
        # pinned to the snapshot it opened at, so one built earlier would read
        # nothing and reconcile would write an empty baseline.
        reconcile_runtime = build_reconcile_runtime(
            adapter_type, use_rest_api_table=use_rest_api_table
        )
        _wipe_store(reconcile_runtime.reconciler_store, store_name="reconciler store")
        _run_reconcile(reconcile_runtime, adapter_type, job_id, changeset_ids)

    if not skip_publish_event:
        _confirm_publish(adapter_type, len(changeset_ids))
        for event_num, changeset_id in enumerate(changeset_ids, start=1):
            if publish_interval_seconds and event_num > 1:
                time.sleep(publish_interval_seconds)
            _publish_adapter_event(adapter_type, job_id, [changeset_id])
            logger.info(
                "Publish progress", published=event_num, total=len(changeset_ids)
            )


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
        help="Skip publishing the adapter.completed EventBridge events after the rebuild",
    )
    parser.add_argument(
        "--publish-interval-seconds",
        type=float,
        default=0.0,
        metavar="SECONDS",
        help="Pause between published events to pace the downstream transformer fan-out (default: no pause)",
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
        publish_interval_seconds=args.publish_interval_seconds,
    )


if __name__ == "__main__":
    main()
