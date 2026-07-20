"""Adapter audit for OAI-PMH adapters.

A local diagnostic to identify records the source holds but the adapter store is
missing. Windowed harvesting cannot spot this on its own: records loaded with
datestamps inside windows that already ran sit behind the cursor and are never
picked up. Run it on demand to check drift and, with ``--list-missing``, get the
ids to feed to the recover step.

The source-of-truth count is adapter-specific: the base adapter uses the OAI
``completeListSize``; Axiell overrides it with the WebAPI (wwwopac.ashx) count,
a direct database count unaffected by the OAI module's datestamp-query faults.
It also emits a ``drift_count`` metric so it can be scheduled later if wanted.
"""

from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from typing import Any

import structlog
from pydantic import BaseModel, ConfigDict, Field

from adapters.extractors.oai_pmh.registry import get_config
from adapters.extractors.oai_pmh.reporting import OAIPMHAuditReport
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.utils.adapter_store import AdapterStore
from models.incremental_window import IncrementalWindow
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

MISSING_ID_SAMPLE = 50


class AuditResponse(BaseModel):
    adapter_type: str
    server_count: int | None
    harvested_count: int
    drift_count: int
    missing_id_sample: list[str] = Field(default_factory=list)


class AuditRuntime(BaseModel):
    config: OAIPMHRuntimeConfig
    store: AdapterStore
    adapter_name: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def handler(
    runtime: AuditRuntime,
    execution_context: ExecutionContext | None = None,
    *,
    list_missing: bool = False,
    now: datetime | None = None,
) -> AuditResponse:
    """Compute source vs adapter-store drift and publish the metric."""
    setup_logging(execution_context)
    now = now or datetime.now(UTC)

    server_count = runtime.config.source_of_truth_count()
    harvested_count = runtime.store.count_active_namespace_records()

    report = OAIPMHAuditReport.from_counts(
        window=IncrementalWindow(start_time=now, end_time=now),
        adapter_type=runtime.adapter_name,
        server_count=server_count,
        harvested_count=harvested_count,
    )
    report.publish()

    logger.info(
        "Adapter audit",
        adapter=runtime.adapter_name,
        server_count=server_count,
        harvested_count=harvested_count,
        drift_count=report.drift_count,
    )

    missing_sample: list[str] = []
    if list_missing and report.drift_count > 0:
        missing_sample = _sample_missing_ids(runtime)
        if missing_sample:
            logger.warning(
                "Records missing from the adapter store",
                adapter=runtime.adapter_name,
                sample_size=len(missing_sample),
                sample=missing_sample,
            )

    return AuditResponse(
        adapter_type=runtime.adapter_name,
        server_count=server_count,
        harvested_count=harvested_count,
        drift_count=report.drift_count,
        missing_id_sample=missing_sample,
    )


def _sample_missing_ids(runtime: AuditRuntime) -> list[str]:
    """Return up to MISSING_ID_SAMPLE ids present in the source but not the store.

    Returns an empty list if the adapter does not support id enumeration. The
    store's ids are streamed id-only into a set, so record content is never
    materialised.
    """
    source_ids = runtime.config.enumerate_source_ids()
    if source_ids is None:
        return []

    have: set[str] = set()
    for batch in runtime.store.stream_active_namespace_records():
        have.update(i for i in batch.column("id").to_pylist() if i is not None)

    missing: list[str] = []
    for record_id in source_ids:
        if record_id not in have:
            missing.append(record_id)
            if len(missing) >= MISSING_ID_SAMPLE:
                break
    return missing


def build_runtime(adapter_type: str, use_rest_api_table: bool = True) -> AuditRuntime:
    config = get_config(adapter_type)
    return AuditRuntime(
        config=config,
        store=config.build_adapter_store(use_rest_api_table=use_rest_api_table),
        adapter_name=config.config.adapter_name,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point. Resolves the adapter from the ``adapter_type`` field."""
    adapter_type = event.get("adapter_type")
    if adapter_type is None:
        raise ValueError("Event must contain 'adapter_type'")

    config = get_config(adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_audit",
    )
    response = handler(
        build_runtime(adapter_type),
        execution_context=execution_context,
        list_missing=bool(event.get("list_missing", False)),
    )
    return response.model_dump(mode="json")


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the adapter audit from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument(
        "--list-missing",
        action="store_true",
        help="On drift, log a sample of ids present at the source but not the store",
    )
    args = parser.parse_args()

    config = get_config(args.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_audit",
    )
    response = handler(
        build_runtime(args.adapter_type, use_rest_api_table=args.use_rest_api_table),
        execution_context=execution_context,
        list_missing=args.list_missing,
    )
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    local_handler(argparse.ArgumentParser(description="Run an OAI-PMH adapter audit"))
