"""Recover specific records by id for OAI-PMH adapters.

The reloader and the loader backfill mode both work by time window. Some
recovery jobs instead have a list of record ids: for example the ids a
reconciliation audit found missing from the store, or records the source
serves individually but not through a date-range query. This step fetches each
id via OAI ``GetRecord`` and writes it to the adapter store, committing in
batches.

Each id is classified: recovered (written), removed (the source reports
``idDoesNotExist``), or unfetchable (the source neither returned it nor said it
was gone, after retries). Unfetchable ids are left absent from the store and
reported, never backfilled with stale content.
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

import httpx
import pyarrow as pa
import structlog
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import IdDoesNotExistError, OAIError
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.record_writer import _serialize_metadata
from adapters.extractors.oai_pmh.registry import get_config
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

DEFAULT_COMMIT_EVERY = 300
POLITE_DELAY_SECONDS = 0.3


class RecoverResponse(BaseModel):
    adapter_type: str
    requested: int
    recovered: int
    removed: int
    unfetchable: list[str]


class RecoverRuntime(BaseModel):
    oai_client: OAIClient
    store: AdapterStore
    adapter_name: str
    namespace: str
    metadata_prefix: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def handler(
    ids: list[str],
    runtime: RecoverRuntime,
    execution_context: ExecutionContext | None = None,
    *,
    commit_every: int = DEFAULT_COMMIT_EVERY,
) -> RecoverResponse:
    """Fetch each id via GetRecord and write recoverable records in batches."""
    setup_logging(execution_context)

    unique_ids = list(dict.fromkeys(i for i in ids if i))
    recovered: list[str] = []
    removed: list[str] = []
    unfetchable: list[str] = []
    buffer: list[dict[str, Any]] = []

    def flush() -> None:
        if not buffer:
            return
        table = pa.Table.from_pylist(buffer, schema=ADAPTER_STORE_ARROW_SCHEMA)
        runtime.store.incremental_update(table)
        logger.info(
            "Committed recovered records",
            adapter=runtime.adapter_name,
            committed=len(buffer),
            recovered_total=len(recovered),
        )
        buffer.clear()

    for record_id in unique_ids:
        time.sleep(POLITE_DELAY_SECONDS)
        try:
            record = runtime.oai_client.get_record(
                identifier=record_id, metadata_prefix=runtime.metadata_prefix
            )
        except IdDoesNotExistError:
            removed.append(record_id)
            continue
        except (etree.XMLSyntaxError, OAIError, httpx.HTTPError) as exc:
            # Empty body, an OAI protocol error, or a network failure after the
            # client's own retries. The source this tool targets is known to be
            # flaky, so classify the id as unfetchable and keep going rather than
            # aborting a large batch on one bad id. Unexpected exceptions (bugs)
            # are deliberately not caught here.
            logger.warning(
                "Record unfetchable",
                adapter=runtime.adapter_name,
                record_id=record_id,
                error=type(exc).__name__,
            )
            unfetchable.append(record_id)
            continue

        content = _serialize_metadata(record)
        recovered.append(record_id)
        buffer.append(
            {
                "namespace": runtime.namespace,
                "id": record_id,
                "content": content,
                "last_modified": record.header.datestamp,
                "deleted": content is None,
            }
        )
        if len(buffer) >= commit_every:
            flush()

    flush()

    logger.info(
        "Recover-by-id complete",
        adapter=runtime.adapter_name,
        requested=len(unique_ids),
        recovered=len(recovered),
        removed=len(removed),
        unfetchable=len(unfetchable),
    )
    return RecoverResponse(
        adapter_type=runtime.adapter_name,
        requested=len(unique_ids),
        recovered=len(recovered),
        removed=len(removed),
        unfetchable=unfetchable,
    )


def build_runtime(adapter_type: str, use_rest_api_table: bool = True) -> RecoverRuntime:
    config = get_config(adapter_type)
    return RecoverRuntime(
        oai_client=config.build_oai_client(),
        store=config.build_adapter_store(use_rest_api_table=use_rest_api_table),
        adapter_name=config.config.adapter_name,
        namespace=config.config.adapter_namespace,
        metadata_prefix=config.config.oai_metadata_prefix,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point. Expects ``adapter_type`` and an ``ids`` list."""
    adapter_type = event.get("adapter_type")
    if adapter_type is None:
        raise ValueError("Event must contain 'adapter_type'")
    ids = event.get("ids")
    if not isinstance(ids, list):
        raise ValueError("Event must contain an 'ids' list")

    config = get_config(adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_recover",
    )
    response = handler(
        ids,
        runtime=build_runtime(adapter_type),
        execution_context=execution_context,
        commit_every=int(event.get("commit_every", DEFAULT_COMMIT_EVERY)),
    )
    return response.model_dump(mode="json")


def _read_ids(args: argparse.Namespace) -> list[str]:
    ids: list[str] = []
    if args.ids:
        ids.extend(i.strip() for i in args.ids.split(",") if i.strip())
    if args.ids_file:
        ids.extend(
            line.strip()
            for line in Path(args.ids_file).read_text().splitlines()
            if line.strip()
        )
    if not ids:
        raise ValueError("Provide --ids and/or --ids-file")
    return ids


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the recover-by-id step from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument("--ids", type=str, help="Comma-separated record ids")
    parser.add_argument(
        "--ids-file", type=str, help="Path to a file of record ids, one per line"
    )
    parser.add_argument("--commit-every", type=int, default=DEFAULT_COMMIT_EVERY)
    parser.add_argument(
        "--report",
        type=str,
        help="Optional path to write the unfetchable-id report as JSON",
    )
    args = parser.parse_args()

    config = get_config(args.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_recover",
    )
    response = handler(
        _read_ids(args),
        runtime=build_runtime(
            args.adapter_type, use_rest_api_table=args.use_rest_api_table
        ),
        execution_context=execution_context,
        commit_every=args.commit_every,
    )
    if args.report:
        Path(args.report).write_text(
            json.dumps(response.model_dump(mode="json"), indent=2)
        )
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    local_handler(argparse.ArgumentParser(description="Recover OAI-PMH records by id"))
