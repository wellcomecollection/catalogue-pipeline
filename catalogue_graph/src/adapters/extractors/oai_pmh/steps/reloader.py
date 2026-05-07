"""Generic OAI-PMH reloader step.

Analyzes window coverage gaps within a specified time range and re-harvests
missing windows by invoking the loader handler for each gap. Intended primarily
for local troubleshooting and manual gap remediation.
"""

from __future__ import annotations

import argparse
from datetime import datetime
from typing import Any

import structlog
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.models.step_events import (
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
)
from adapters.extractors.oai_pmh.registry import get_config
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.extractors.oai_pmh.steps.loader import (
    LoaderRuntime,
    LoaderStepConfig,
    build_harvester,
)
from adapters.extractors.oai_pmh.steps.loader import (
    build_runtime as _build_loader_runtime,
)
from adapters.utils.adapter_events import BaseAdapterEvent
from adapters.utils.window_reporter import WindowReporter
from adapters.utils.window_store import WindowStore
from models.incremental_window import IncrementalWindow
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class ReloaderStepConfig(BaseModel):
    """Configuration for the reloader step."""

    use_rest_api_table: bool = True
    window_minutes: int | None = None


class ReloaderEvent(BaseAdapterEvent):
    window: IncrementalWindow
    dry_run: bool = False
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
    window_minutes = cfg.window_minutes or adapter_config.config.window_minutes

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
            metadata_prefix=adapter_config.config.oai_metadata_prefix,
            set_spec=adapter_config.config.oai_set_spec,
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
            time_range=loader_event.window,
            max_windows=loader_event.max_windows,
            reprocess_successful_windows=False,
        )

        loader_response = OAIPMHLoaderResponse.from_summaries(summaries, job_id=job_id)
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
    logger.info(
        "Window coverage report",
        total_windows=report.total_windows,
        coverage_hours=round(report.coverage_hours, 2),
        state_counts=report.state_counts,
        gaps=len(report.coverage_gaps),
        failures=len(report.failures),
        last_success_end=report.last_success_end.isoformat()
        if report.last_success_end
        else None,
        range_start=window_start.isoformat(),
        range_end=window_end.isoformat(),
    )
    for gap in report.coverage_gaps:
        duration = (gap.end - gap.start).total_seconds() / 3600
        logger.info(
            "Coverage gap",
            start=gap.start.isoformat(),
            end=gap.end.isoformat(),
            duration_hours=round(duration, 2),
        )
    for failure in report.failures:
        logger.warning(
            "Window failure",
            window_key=failure.window_key,
            attempts=failure.attempts,
            last_error=failure.last_error,
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
    reloader_event = ReloaderEvent.model_validate(event)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=pipeline_step,
    )

    runtime = build_runtime(
        adapter_config,
        ReloaderStepConfig(window_minutes=reloader_event.window_minutes),
    )
    response = handler(
        job_id=reloader_event.job_id,
        window_start=reloader_event.window.start_time_utc,
        window_end=reloader_event.window.end_time_utc,
        execution_context=execution_context,
        runtime=runtime,
        dry_run=reloader_event.dry_run,
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

    window = IncrementalWindow(start_time=args.window_start, end_time=args.window_end)

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
        window_start=window.start_time_utc,
        window_end=window.end_time_utc,
        execution_context=execution_context,
        runtime=runtime,
        dry_run=args.dry_run,
    )

    logger.info(
        "Reloader complete",
        job_id=response.job_id,
        total_gaps=response.total_gaps,
        dry_run=response.dry_run,
        gaps_succeeded=sum(
            1 for g in response.gaps_processed if not g.skipped and g.error is None
        ),
        gaps_skipped=sum(1 for g in response.gaps_processed if g.skipped),
        gaps_failed=sum(1 for g in response.gaps_processed if g.error is not None),
    )


def main() -> None:
    """Unified CLI entry point for OAI-PMH reloader steps."""
    import typing

    from adapters.extractors.oai_pmh.registry import AdapterType

    pre_parser = argparse.ArgumentParser(add_help=False)
    pre_parser.add_argument(
        "--adapter-type",
        required=True,
        choices=typing.get_args(AdapterType),
        help="Which adapter to reload",
    )
    pre_args, _ = pre_parser.parse_known_args()

    config = get_config(pre_args.adapter_type)
    parser = argparse.ArgumentParser(
        description=f"Reload {pre_args.adapter_type} harvesting windows to fill coverage gaps"
    )
    parser.add_argument(
        "--adapter-type",
        required=True,
        choices=typing.get_args(AdapterType),
        help="Which adapter to reload",
    )
    add_cli_args(parser)
    args = parser.parse_args()
    run_cli(
        adapter_config=config,
        pipeline_step=f"{config.config.pipeline_step_prefix}_reloader",
        description=f"Reload {pre_args.adapter_type} harvesting windows",
        args=args,
    )


if __name__ == "__main__":
    main()
