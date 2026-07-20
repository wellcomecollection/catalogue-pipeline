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
from oai_pmh_client.models import Record
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.models.step_events import AdapterRecoveryEvent
from adapters.extractors.oai_pmh.record_writer import build_adapter_store_row
from adapters.extractors.oai_pmh.registry import get_config
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

DEFAULT_COMMIT_EVERY = 300
POLITE_DELAY_SECONDS = 0.3


class RecoverEvent(AdapterRecoveryEvent):
    """Event payload for the recover-by-id step."""

    ids: list[str]
    """Record ids to fetch individually via OAI ``GetRecord``."""

    commit_every: int = DEFAULT_COMMIT_EVERY
    """Number of recovered records to buffer before committing a batch."""


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


class RecoveryBatch:
    """Classifies recovery outcomes and commits recovered records in batches.

    Holds the counts the run reports on, plus the pending write buffer. Callers
    record one outcome per requested id (recovered, removed, or unfetchable) and
    call :meth:`flush` at the end to commit whatever is left in the buffer.
    """

    def __init__(
        self, runtime: RecoverRuntime, *, commit_every: int = DEFAULT_COMMIT_EVERY
    ) -> None:
        self._runtime = runtime
        self._commit_every = commit_every
        self._buffer: list[dict[str, Any]] = []
        self.recovered: list[str] = []
        self.removed: list[str] = []
        self.unfetchable: list[str] = []

    def add_recovered(self, record_id: str, record: Record) -> None:
        """Buffer a fetched record, committing once the buffer is full."""
        self.recovered.append(record_id)
        self._buffer.append(
            build_adapter_store_row(
                namespace=self._runtime.namespace,
                identifier=record_id,
                record=record,
            )
        )
        if len(self._buffer) >= self._commit_every:
            self.flush()

    def add_removed(self, record_id: str) -> None:
        """Record an id the source reports as no longer existing."""
        self.removed.append(record_id)

    def add_unfetchable(self, record_id: str, error: Exception) -> None:
        """Record an id the source neither returned nor declared gone."""
        logger.warning(
            "Record unfetchable",
            adapter=self._runtime.adapter_name,
            record_id=record_id,
            error=type(error).__name__,
        )
        self.unfetchable.append(record_id)

    def flush(self) -> None:
        """Commit any buffered records to the adapter store."""
        if not self._buffer:
            return
        table = pa.Table.from_pylist(self._buffer, schema=ADAPTER_STORE_ARROW_SCHEMA)
        self._runtime.store.incremental_update(table)
        logger.info(
            "Committed recovered records",
            adapter=self._runtime.adapter_name,
            committed=len(self._buffer),
            recovered_total=len(self.recovered),
        )
        self._buffer.clear()

    def to_response(self, requested: int) -> RecoverResponse:
        return RecoverResponse(
            adapter_type=self._runtime.adapter_name,
            requested=requested,
            recovered=len(self.recovered),
            removed=len(self.removed),
            unfetchable=self.unfetchable,
        )


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
    batch = RecoveryBatch(runtime, commit_every=commit_every)

    for record_id in unique_ids:
        time.sleep(POLITE_DELAY_SECONDS)
        try:
            record = runtime.oai_client.get_record(
                identifier=record_id, metadata_prefix=runtime.metadata_prefix
            )
        except IdDoesNotExistError:
            batch.add_removed(record_id)
            continue
        except (etree.XMLSyntaxError, OAIError, httpx.HTTPError) as exc:
            # Empty body, an OAI protocol error, or a network failure after the
            # client's own retries. The source this tool targets is known to be
            # flaky, so classify the id as unfetchable and keep going rather than
            # aborting a large batch on one bad id. Unexpected exceptions (bugs)
            # are deliberately not caught here.
            batch.add_unfetchable(record_id, exc)
            continue

        batch.add_recovered(record_id, record)

    batch.flush()

    logger.info(
        "Recover-by-id complete",
        adapter=runtime.adapter_name,
        requested=len(unique_ids),
        recovered=len(batch.recovered),
        removed=len(batch.removed),
        unfetchable=len(batch.unfetchable),
    )
    return batch.to_response(requested=len(unique_ids))


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
    request = RecoverEvent.model_validate(event)

    config = get_config(request.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_recover",
    )
    response = handler(
        request.ids,
        runtime=build_runtime(request.adapter_type),
        execution_context=execution_context,
        commit_every=request.commit_every,
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
