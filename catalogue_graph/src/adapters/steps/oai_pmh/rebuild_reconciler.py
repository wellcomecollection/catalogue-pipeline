"""Rebuild the Axiell reconciler id->GUID baseline from the adapter store.

The reconciler maps each adapter record id to the source-identifier GUID it
produces, so a later GUID change can mark the old work deleted. It only runs
incrementally, per changeset; the transformer refuses to run it over the whole
table because it cannot reconstruct historic deleted GUIDs from a full snapshot.
So after a full reindex there is otherwise no supported way to reseed the table.

This step walks the active records, computes each record's GUID with the same
builder the reconciler uses, and writes the id->GUID mappings in batches.
Records whose MARC cannot be parsed, or that yield no GUID (such as records with
an empty 001), are skipped and counted rather than aborting the run.
"""

from __future__ import annotations

import argparse
import json
from typing import Any

import pyarrow as pa
import structlog
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.registry import get_config
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.marc import parse_single_marc_record

logger = structlog.get_logger(__name__)

DEFAULT_BATCH_SIZE = 10_000


class RebuildReconcilerResponse(BaseModel):
    adapter_type: str
    active_records: int
    mappings_written: int
    skipped: int


class RebuildReconcilerRuntime(BaseModel):
    adapter_store: AdapterStore
    reconciler_store: ReconcilerStore
    adapter_name: str
    namespace: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def _compute_guid(row: dict[str, Any]) -> str | None:
    """Compute a record's source-identifier GUID, or None if not derivable.

    Follows ``AxiellReconciler._get_record_guid`` (parse the MARCXML, read the
    GUID the Axiell work builder produces), but is intentionally more defensive:
    the builder raises on a record with no usable 001, and here that is a
    skip-and-count rather than a crash, so a whole rebuild is not lost to a
    handful of malformed records.
    """
    content = row.get("content")
    if not content:
        return None
    try:
        record = parse_single_marc_record(content)
    except Exception:
        return None
    try:
        return AxiellWorkBuilder(record, row["last_modified"]).source_identifier.value
    except Exception:
        return None


def handler(
    runtime: RebuildReconcilerRuntime,
    execution_context: ExecutionContext | None = None,
    *,
    batch_size: int = DEFAULT_BATCH_SIZE,
) -> RebuildReconcilerResponse:
    """Recompute and write id->GUID mappings for every active record."""
    setup_logging(execution_context)

    active = 0
    written = 0
    skipped = 0
    buffer: list[dict[str, Any]] = []

    def flush() -> None:
        nonlocal written
        if not buffer:
            return
        table = pa.Table.from_pylist(buffer, schema=runtime.reconciler_store.schema)
        result = runtime.reconciler_store.incremental_update(table)
        if result:
            written += len(result.inserted_record_ids) + len(result.updated_record_ids)
        logger.info(
            "Committed reconciler mappings",
            adapter=runtime.adapter_name,
            committed=len(buffer),
            written_total=written,
        )
        buffer.clear()

    for record_batch in runtime.adapter_store.stream_active_namespace_records():
        for row in record_batch.to_pylist():
            active += 1
            guid = _compute_guid(row)
            if not guid:
                skipped += 1
                continue
            buffer.append(
                {
                    "namespace": runtime.namespace,
                    "id": row["id"],
                    "guid": guid,
                    "last_modified": row["last_modified"],
                }
            )
            if len(buffer) >= batch_size:
                flush()
    flush()

    logger.info(
        "Reconciler baseline rebuild complete",
        adapter=runtime.adapter_name,
        active_records=active,
        mappings_written=written,
        skipped=skipped,
    )
    return RebuildReconcilerResponse(
        adapter_type=runtime.adapter_name,
        active_records=active,
        mappings_written=written,
        skipped=skipped,
    )


def build_runtime(
    adapter_type: str, use_rest_api_table: bool = True
) -> RebuildReconcilerRuntime:
    config = get_config(adapter_type)
    if not hasattr(config, "build_reconciler_table"):
        raise ValueError(
            f"Adapter '{adapter_type}' has no reconciler table; rebuild is Axiell-only"
        )
    reconciler_table = config.build_reconciler_table(
        use_rest_api_table=use_rest_api_table
    )
    return RebuildReconcilerRuntime(
        adapter_store=config.build_adapter_store(use_rest_api_table=use_rest_api_table),
        reconciler_store=ReconcilerStore(
            reconciler_table, namespace=config.config.adapter_namespace
        ),
        adapter_name=config.config.adapter_name,
        namespace=config.config.adapter_namespace,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point. Resolves the adapter from the ``adapter_type`` field."""
    adapter_type = event.get("adapter_type")
    if adapter_type is None:
        raise ValueError("Event must contain 'adapter_type'")

    config = get_config(adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_rebuild_reconciler",
    )
    response = handler(
        build_runtime(adapter_type),
        execution_context=execution_context,
        batch_size=int(event.get("batch_size", DEFAULT_BATCH_SIZE)),
    )
    return response.model_dump(mode="json")


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the reconciler baseline rebuild from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    args = parser.parse_args()

    config = get_config(args.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_rebuild_reconciler",
    )
    response = handler(
        build_runtime(args.adapter_type, use_rest_api_table=args.use_rest_api_table),
        execution_context=execution_context,
        batch_size=args.batch_size,
    )
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    local_handler(
        argparse.ArgumentParser(
            description="Rebuild the Axiell reconciler id->GUID baseline"
        )
    )
