"""Generic OAI-PMH reloader step.

Analyzes window coverage gaps within a specified time range and re-harvests
missing windows by invoking the loader handler for each gap. Intended primarily
for local troubleshooting and manual gap remediation.
"""

from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from typing import Any

import structlog
from pydantic import BaseModel, ConfigDict

from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHLoaderResponse
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.oai_pmh.steps.loader import (
    LoaderRuntime,
    LoaderStepConfig,
    build_harvester,
)
from adapters.oai_pmh.steps.loader import build_runtime as _build_loader_runtime
from adapters.utils.window_reporter import WindowReporter
from adapters.utils.window_store import WindowStore
from models.incremental_window import IncrementalWindow
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class ReloaderStepConfig(BaseModel):
    """Configuration for the reloader step."""

    use_rest_api_table: bool = True
    window_minutes: int | None = None


class ReloaderRuntime(BaseModel):
    """Runtime dependencies for the reloader step."""

    store: WindowStore
    loader_runtime: LoaderRuntime
    adapter_config: Any  # OAIPMHRuntimeConfig - Any to avoid Pydantic issues

    model_config = ConfigDict(arbitrary_types_allowed=True)


class GapReloadResult(BaseModel):
    """Result of processing a single coverage gap."""

    gap_start: datetime
    gap_end: datetime
    loader_response: OAIPMHLoaderResponse | None = None
    skipped: bool = False
    error: str | None = None


class ReloaderResponse(BaseModel):
    """Response from the reloader step."""

    job_id: str
    window_start: datetime
    window_end: datetime
    total_gaps: int
    gaps_processed: list[GapReloadResult]
    dry_run: bool = False


def build_runtime(
    adapter_config: OAIPMHRuntimeConfig,
    step_config: ReloaderStepConfig | None = None,
) -> ReloaderRuntime:
    """Build runtime dependencies for the reloader step.

    Args:
        adapter_config: Adapter-specific configuration.
        step_config: Step-specific configuration overrides.

    Returns:
        ReloaderRuntime with all dependencies initialized.
    """
    cfg = step_config or ReloaderStepConfig()
    store = adapter_config.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
<<<<<<< HEAD
    window_minutes = cfg.window_minutes or adapter_config.config.window_minutes
=======
    window_minutes = cfg.window_minutes or adapter_config.window_minutes
>>>>>>> fd236ee3d (remove some more unused stuff)

    loader_runtime = _build_loader_runtime(
        adapter_config,
        LoaderStepConfig(
            use_rest_api_table=cfg.use_rest_api_table,
            window_minutes=window_minutes,
            allow_partial_final_window=True,
        ),
    )

    return ReloaderRuntime(
        store=store,
        loader_runtime=loader_runtime,
        adapter_config=adapter_config,
    )


def _process_gap(
    gap_start: datetime,
    gap_end: datetime,
    job_id: str,
    runtime: ReloaderRuntime,
    dry_run: bool = False,
) -> GapReloadResult:
    """Process a single coverage gap by invoking the loader handler.

    Args:
        gap_start: Start of the gap to fill.
        gap_end: End of the gap to fill.
        job_id: Job identifier for tracking.
        runtime: Runtime dependencies.
        dry_run: If True, skip actual loader invocation.

    Returns:
        GapReloadResult with processing status and any errors.
    """
    if dry_run:
        logger.info(
            "[DRY RUN] Would reload gap",
            gap_start=gap_start.isoformat(),
            gap_end=gap_end.isoformat(),
        )
        return GapReloadResult(
            gap_start=gap_start,
            gap_end=gap_end,
            skipped=True,
        )

    try:
        adapter_config = runtime.adapter_config
        # Construct loader event with sensible defaults from config
        loader_event = OAIPMHLoaderEvent(
            job_id=job_id,
            window=IncrementalWindow(start_time=gap_start, end_time=gap_end),
<<<<<<< HEAD
            metadata_prefix=adapter_config.config.oai_metadata_prefix,
            set_spec=adapter_config.config.oai_set_spec,
=======
            metadata_prefix=adapter_config.oai_metadata_prefix,
            set_spec=adapter_config.oai_set_spec,
>>>>>>> fd236ee3d (remove some more unused stuff)
            max_windows=None,  # Process all windows in the gap
            window_minutes=runtime.loader_runtime.window_generator.window_minutes,
        )

        logger.info(
            "Reloading gap",
            gap_start=gap_start.isoformat(),
            gap_end=gap_end.isoformat(),
        )

        # Use the harvester directly to avoid recreating runtime
        harvester = build_harvester(loader_event, runtime.loader_runtime)
        summaries = harvester.harvest_range(
            start_time=loader_event.window.start_time,
            end_time=loader_event.window.end_time,
            max_windows=loader_event.max_windows,
            reprocess_successful_windows=False,
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

        loader_response = OAIPMHLoaderResponse(
            summaries=summaries,
            changeset_ids=list(changeset_ids),
            changed_record_count=changed_record_count,
            job_id=job_id,
        )

        return GapReloadResult(
            gap_start=gap_start,
            gap_end=gap_end,
            loader_response=loader_response,
            skipped=False,
        )

    except Exception as e:
        logger.exception("Failed to reload gap", error=str(e))
        return GapReloadResult(
            gap_start=gap_start,
            gap_end=gap_end,
            skipped=False,
            error=str(e),
        )


def handler(
    job_id: str,
    window_start: datetime,
    window_end: datetime,
    runtime: ReloaderRuntime,
    execution_context: ExecutionContext | None = None,
    dry_run: bool = False,
) -> ReloaderResponse:
    """Identify and reload coverage gaps within a time range.

    Args:
        job_id: Unique identifier for this reload operation.
        window_start: Start of the time range to analyze.
        window_end: End of the time range to analyze.
        runtime: Runtime dependencies.
        execution_context: Logging context for tracing.
        dry_run: If True, report gaps without actually reloading them.

    Returns:
        ReloaderResponse with gap processing results.
    """
    setup_logging(execution_context)

    # Generate coverage report for the specified range
    reporter = WindowReporter(
        store=runtime.store,
        window_minutes=runtime.loader_runtime.window_generator.window_minutes,
    )
    report = reporter.coverage_report(range_start=window_start, range_end=window_end)

    # Log window coverage report
    logger.info("Window coverage report", summary=report.summary())

    logger.info(
        "Gap analysis complete",
        gap_count=len(report.coverage_gaps),
        range_start=window_start.isoformat(),
        range_end=window_end.isoformat(),
    )

    if not report.coverage_gaps:
        logger.info("No coverage gaps detected - nothing to reload")
        return ReloaderResponse(
            job_id=job_id,
            window_start=window_start,
            window_end=window_end,
            total_gaps=0,
            gaps_processed=[],
            dry_run=dry_run,
        )

    # Process each gap sequentially
    gaps_processed: list[GapReloadResult] = []
    for i, gap in enumerate(report.coverage_gaps, 1):
        logger.info(
            "Processing gap",
            gap_number=i,
            total_gaps=len(report.coverage_gaps),
        )
        result = _process_gap(
            gap_start=gap.start,
            gap_end=gap.end,
            job_id=job_id,
            runtime=runtime,
            dry_run=dry_run,
        )
        gaps_processed.append(result)

    return ReloaderResponse(
        job_id=job_id,
        window_start=window_start,
        window_end=window_end,
        total_gaps=len(report.coverage_gaps),
        gaps_processed=gaps_processed,
        dry_run=dry_run,
    )


def lambda_handler(
    event: dict[str, Any],
    context: Any,
    adapter_config: OAIPMHRuntimeConfig,
    pipeline_step: str,
) -> dict[str, Any]:
    """Generic AWS Lambda handler for reloader step.

    Args:
        event: Lambda event payload.
        context: Lambda context.
        adapter_config: Adapter-specific configuration.
        pipeline_step: Name of the pipeline step for logging.

    Returns:
        Serialized ReloaderResponse.
    """
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=pipeline_step,
    )
    job_id = event["job_id"]
    window_start = datetime.fromisoformat(event["window_start"].replace("Z", "+00:00"))
    window_end = datetime.fromisoformat(event["window_end"].replace("Z", "+00:00"))
    dry_run = event.get("dry_run", False)
    window_minutes = event.get("window_minutes")

    runtime = build_runtime(
        adapter_config,
        ReloaderStepConfig(window_minutes=window_minutes),
    )
    response = handler(
        job_id=job_id,
        window_start=window_start,
        window_end=window_end,
        execution_context=execution_context,
        runtime=runtime,
        dry_run=dry_run,
    )
    return response.model_dump(mode="json")


def add_cli_args(parser: argparse.ArgumentParser) -> None:
    """Add common CLI arguments for the reloader step.

    Args:
        parser: ArgumentParser to add arguments to.
    """
    parser.add_argument(
        "--job-id",
        type=str,
        required=True,
        help="Unique identifier for this reload job",
    )
    parser.add_argument(
        "--window-start",
        type=str,
        required=True,
        help="ISO8601 timestamp for the start of the range to analyze "
        "(e.g., 2025-11-17T12:00:00Z)",
    )
    parser.add_argument(
        "--window-end",
        type=str,
        required=True,
        help="ISO8601 timestamp for the end of the range to analyze "
        "(e.g., 2025-11-17T14:00:00Z)",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables window status catalog instead of local storage",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report gaps without actually reloading them",
    )
    parser.add_argument(
        "--window-minutes",
        type=int,
        help="Override default window size in minutes",
    )


def run_cli(
    adapter_config: OAIPMHRuntimeConfig,
    pipeline_step: str,
    description: str,
    args: argparse.Namespace | None = None,
) -> None:
    """Run the reloader step from CLI arguments.

    Args:
        adapter_config: Adapter-specific configuration.
        pipeline_step: Name of the pipeline step for logging.
        description: Description for the argument parser.
        args: Pre-parsed arguments (if None, will parse from sys.argv).
    """
    if args is None:
        parser = argparse.ArgumentParser(description=description)
        add_cli_args(parser)
        args = parser.parse_args()

    window_start = datetime.fromisoformat(
        args.window_start.replace("Z", "+00:00")
    ).astimezone(UTC)
    window_end = datetime.fromisoformat(
        args.window_end.replace("Z", "+00:00")
    ).astimezone(UTC)

    runtime = build_runtime(
        adapter_config,
        ReloaderStepConfig(
            use_rest_api_table=args.use_rest_api_table,
            window_minutes=args.window_minutes,
        ),
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=pipeline_step,
    )

    response = handler(
        job_id=args.job_id,
        window_start=window_start,
        window_end=window_end,
        execution_context=execution_context,
        runtime=runtime,
        dry_run=args.dry_run,
    )

    logger.info("Reloader response", response=response.model_dump(mode="json"))
