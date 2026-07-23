"""Bulk-load all records from an OAI-PMH endpoint into the adapter table.

Downloads every record from the given adapter's OAI-PMH endpoint in a single
list_records pass, then calls snapshot_sync on the AdapterStore so that
inserts, updates and soft-deletes are applied in one shot.

Usage (from catalogue_graph/):

    uv run python scripts/bulk_load_oai_pmh.py --adapter-type axiell
    uv run python scripts/bulk_load_oai_pmh.py --adapter-type folio --use-rest-api-table
"""
from __future__ import annotations

import argparse
from typing import Any

import polars as pl
import pyarrow as pa
import structlog
from adapters.extractors.oai_pmh.record_writer import _serialize_metadata
from adapters.extractors.oai_pmh.registry import AdapterType, get_config
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.argparse import add_adapter_event_args
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from oai_pmh_client.client import OAIClient
from pyiceberg.expressions import EqualTo
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

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


def _sync_adapter_store(
    rows: list[dict[str, Any]], adapter_store: AdapterStore
) -> None:
    table = pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)

    logger.info("Running snapshot sync", row_count=len(rows))
    update = adapter_store.incremental_update(table)

    if update:
        logger.info(
            "Snapshot sync complete",
            changeset_id=update.changeset_id,
            upserted=len(update.upserted_record_ids),
        )
    else:
        logger.info("Snapshot sync produced no changes (table already up to date)")


def bulk_load(
    adapter_type: AdapterType,
    *,
    use_rest_api_table: bool = False,
    data_source: str = "download",
    snapshot_path: str | None = None,
    wipe_table: bool = False,
) -> None:
    config = get_config(adapter_type)

    if data_source == "snapshot":
        if not snapshot_path:
            raise ValueError(
                "--snapshot-path is required when --data-source is snapshot"
            )
        rows = load_snapshot(snapshot_path)
    else:
        oai_client = config.build_oai_client()
        rows = _get_oai_rows(oai_client, config.config)
        if not rows:
            logger.info("No records returned from OAI-PMH endpoint. Nothing to sync.")
            return
        if snapshot_path:
            save_snapshot(rows, snapshot_path)

    adapter_store = config.build_adapter_store(use_rest_api_table=use_rest_api_table)
    if wipe_table:
        _wipe_adapter_table(adapter_store)
    _sync_adapter_store(rows, adapter_store)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Bulk-load all OAI-PMH records into the adapter table"
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
        default=None,
        metavar="PATH",
        help="When --data-source=snapshot: path to load from. When --data-source=download: path to save a snapshot to (optional).",
    )
    parser.add_argument(
        "--wipe-table",
        action="store_true",
        help="Hard-delete all existing rows for this adapter's namespace before loading (prompts for confirmation)",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="bulk_load_oai_pmh")
    )

    bulk_load(
        args.adapter_type,
        use_rest_api_table=args.use_rest_api_table,
        data_source=args.data_source,
        snapshot_path=args.snapshot_path,
        wipe_table=args.wipe_table,
    )


if __name__ == "__main__":
    main()
