"""Generic loader step for OAI-PMH adapters.

Harvests OAI-PMH windows requested by the trigger step, persists raw records
into Iceberg, and emits changeset identifiers for downstream processing.

This module provides reusable loader logic that can be used by any
OAI-PMH adapter by providing an OAIPMHRuntimeConfig implementation.
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime
from typing import Any

import structlog
from oai_pmh_client.client import OAIClient
from pydantic import BaseModel, ConfigDict

from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHLoaderResponse
from adapters.oai_pmh.record_writer import WindowRecordWriter
from adapters.oai_pmh.reporting import OAIPMHLoaderReport
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_generator import WindowGenerator
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import WindowStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class LoaderStepConfig(BaseModel):
    """Configuration for the loader step runtime."""

    use_rest_api_table: bool = True
    window_minutes: int | None = None
    allow_partial_final_window: bool = False
    suppress_summaries: bool = True


class LoaderRuntime(BaseModel):
    """Runtime dependencies for the loader step."""

    store: WindowStore
    table_client: AdapterStore
    oai_client: OAIClient
    window_generator: WindowGenerator
    adapter_namespace: str
    adapter_name: str
    suppress_summaries: bool = True
    report_s3_bucket: str | None = None
    report_s3_prefix: str = "dev"

    model_config = ConfigDict(arbitrary_types_allowed=True)


def _format_window_range(start: datetime, end: datetime) -> str:
    """Format a window range as a string for tagging."""
    return f"{start.isoformat()}-{end.isoformat()}"


def build_runtime(
    config: OAIPMHRuntimeConfig,
    step_config: LoaderStepConfig | None = None,
) -> LoaderRuntime:
    """Build the loader runtime from adapter configuration.

    Args:
        config: Adapter-specific runtime configuration.
        step_config: Optional step-specific overrides.

    Returns:
        LoaderRuntime ready for execution.
    """
    cfg = step_config or LoaderStepConfig()
    store = config.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table_client = config.build_adapter_store(use_rest_api_table=cfg.use_rest_api_table)
    oai_client = config.build_oai_client()

    window_generator = WindowGenerator(
        window_minutes=cfg.window_minutes or config.config.window_minutes,
        allow_partial_final_window=cfg.allow_partial_final_window,
    )

    return LoaderRuntime(
        store=store,
        table_client=table_client,
        oai_client=oai_client,
        window_generator=window_generator,
        adapter_namespace=config.config.adapter_namespace,
        adapter_name=config.config.adapter_name,
        suppress_summaries=cfg.suppress_summaries,
        report_s3_bucket=config.config.report_s3_bucket,
        report_s3_prefix=config.config.report_s3_prefix,
    )


def build_harvester(
    request: OAIPMHLoaderEvent,
    runtime: LoaderRuntime,
) -> WindowHarvestManager:
    """Build a window harvester for the given request.

    Args:
        request: Loader event with window and OAI-PMH parameters.
        runtime: Loader runtime with clients and configuration.

    Returns:
        WindowHarvestManager configured for this request.
    """
    window_start = request.window.start_time
    window_end = request.window.end_time
    callback = WindowRecordWriter(
        namespace=runtime.adapter_namespace,
        table_client=runtime.table_client,
        job_id=request.job_id,
        window_range=_format_window_range(window_start, window_end),
    )
    return WindowHarvestManager(
        store=runtime.store,
        window_generator=runtime.window_generator,
        client=runtime.oai_client,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        record_callback=callback,
        default_tags={"job_id": request.job_id},
    )


def execute_loader(
    request: OAIPMHLoaderEvent,
    runtime: LoaderRuntime,
) -> OAIPMHLoaderResponse:
    """Execute the loader step to harvest records.

    Args:
        request: Loader event with window and OAI-PMH parameters.
        runtime: Loader runtime with clients and configuration.

    Returns:
        OAIPMHLoaderResponse with harvest results.

    Raises:
        RuntimeError: If no windows were ready to harvest.
    """
    window_start = request.window.start_time
    window_end = request.window.end_time
    harvester = build_harvester(request, runtime)

    summaries = harvester.harvest_range(
        start_time=window_start,
        end_time=window_end,
        max_windows=request.max_windows,
        reprocess_successful_windows=False,
    )

    if not summaries:
        raise RuntimeError(
            "No pending windows to harvest for "
            f"{window_start.isoformat()} -> {window_end.isoformat()}"
        )

    changed_record_count = 0
    changeset_ids: set[str] = set()

    for summary in summaries:
        if not summary.tags:
            continue

        if "changeset_id" in summary.tags:
            changeset_ids.add(summary.tags["changeset_id"])

        if "record_ids_changed" in summary.tags:
            changed_ids = json.loads(summary.tags["record_ids_changed"])
            changed_record_count += len(changed_ids)

    return OAIPMHLoaderResponse(
        summaries=summaries,
        changeset_ids=list(changeset_ids),
        changed_record_count=changed_record_count,
        job_id=request.job_id,
    )


def handler(
    event: OAIPMHLoaderEvent,
    runtime: LoaderRuntime,
    execution_context: ExecutionContext | None = None,
) -> OAIPMHLoaderResponse:
    """Execute the loader step.

    Args:
        event: Loader event with window and OAI-PMH parameters.
        runtime: Runtime dependencies and configuration.
        execution_context: Optional logging context.

    Returns:
        OAIPMHLoaderResponse with harvest results.
    """
    setup_logging(execution_context)
    response = execute_loader(event, runtime=runtime)

    report = OAIPMHLoaderReport.from_loader(
        event,
        response,
        adapter_type=runtime.adapter_name,
        report_s3_bucket=runtime.report_s3_bucket,
        report_s3_prefix=runtime.report_s3_prefix,
    )
    report.publish()

    if runtime.suppress_summaries:
        response.summaries = []

    return response


def lambda_handler(
    event: dict[str, Any],
    context: Any,
    *,
    config: OAIPMHRuntimeConfig,
) -> dict[str, Any]:
    """Lambda entry point for the loader step.

    Args:
        event: Loader event payload.
        context: Lambda context object.
        config: Adapter-specific runtime configuration.

    Returns:
        Serialized OAIPMHLoaderResponse.
    """
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_loader",
    )
    request = OAIPMHLoaderEvent.model_validate(event)
    runtime = build_runtime(config)
    response = handler(request, runtime, execution_context=execution_context)
    return response.model_dump(mode="json")


def build_cli_parser(config: OAIPMHRuntimeConfig) -> argparse.ArgumentParser:
    """Build the CLI argument parser with common arguments.

    Args:
        config: Adapter configuration for default values.

    Returns:
        ArgumentParser with common loader arguments.
    """
    parser = argparse.ArgumentParser(
        description=f"Run the {config.config.adapter_name} loader step locally"
    )
    parser.add_argument(
        "--event",
        type=str,
        required=True,
        help="Path to a loader event JSON payload",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables catalog instead of local storage",
    )
    parser.add_argument(
        "--allow-partial-final-window",
        action="store_true",
        default=True,
        help="Allow partial final window (default: True for CLI)",
    )
    return parser


def run_cli(
    config: OAIPMHRuntimeConfig, args: argparse.Namespace | None = None
) -> None:
    """Run the loader step from the command line.

    Args:
        config: Adapter-specific runtime configuration.
        args: Parsed arguments (if None, will parse from sys.argv).
    """
    if args is None:
        parser = build_cli_parser(config)
        args = parser.parse_args()

    with open(args.event, encoding="utf-8") as f:
        event_data = json.load(f)
        # Set allow_partial_final_window from CLI arg if not in event
        if "allow_partial_final_window" not in event_data:
            event_data["allow_partial_final_window"] = args.allow_partial_final_window
        event = OAIPMHLoaderEvent.model_validate(event_data)

    runtime = build_runtime(
        config,
        LoaderStepConfig(
            use_rest_api_table=args.use_rest_api_table,
            window_minutes=event.window_minutes,
            allow_partial_final_window=(
                event.allow_partial_final_window
                if event.allow_partial_final_window is not None
                else args.allow_partial_final_window
            ),
            suppress_summaries=False,
        ),
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_loader",
    )
    response = handler(event, runtime, execution_context=execution_context)

    logger.info("Loader response", response=response.model_dump(mode="json"))
