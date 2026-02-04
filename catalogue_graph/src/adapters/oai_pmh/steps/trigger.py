"""Generic trigger step for OAI-PMH adapters.

Computes the next harvesting window, ensuring progress is within the
configured lag tolerance, and emits a loader event payload.

This module provides reusable trigger logic that can be used by any
OAI-PMH adapter by providing an OAIPMHRuntimeConfig implementation.
"""

from __future__ import annotations

import argparse
from datetime import UTC, datetime, timedelta
from typing import Any

import boto3
import structlog
from pydantic import BaseModel, ConfigDict

from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHTriggerEvent
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_reporter import WindowReporter
from adapters.utils.window_store import WindowStore
from clients.chatbot_notifier import ChatbotNotifier
from models.events import EventBridgeScheduledEvent, IncrementalWindow
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class TriggerStepConfig(BaseModel):
    """Configuration for the trigger step runtime."""

    use_rest_api_table: bool = True
    enforce_lag: bool = True
    window_minutes: int | None = None
    window_lookback_days: int | None = None


class TriggerRuntime(BaseModel):
    """Runtime dependencies for the trigger step."""

    store: WindowStore
    notifier: WindowNotifier | None = None
    enforce_lag: bool = True
    window_minutes: int
    window_lookback_days: int
    max_lag_minutes: int
    max_pending_windows: int | None = None
    oai_metadata_prefix: str
    oai_set_spec: str | None = None
    adapter_name: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def generate_job_id(timestamp: datetime) -> str:
    """Generate a job ID from a timestamp."""
    return timestamp.astimezone(UTC).strftime("%Y%m%dT%H%M")


def _determine_start(
    now: datetime, last_success_end: datetime | None, window_lookback_days: int
) -> datetime:
    """Determine the start time for the next harvesting window."""
    if last_success_end is not None:
        return last_success_end
    return now - timedelta(days=window_lookback_days)


def _enforce_lag(
    now: datetime,
    last_success_end: datetime | None,
    max_lag_minutes: int,
    adapter_name: str,
) -> None:
    """Check if the adapter is too far behind and raise if so."""
    if last_success_end is None:
        return
    lag_minutes = (now - last_success_end).total_seconds() / 60
    if lag_minutes > max_lag_minutes:
        raise RuntimeError(
            f"{adapter_name.title()} adapter is too far behind: last successful window "
            f"ended {lag_minutes:.1f} minutes ago (limit={max_lag_minutes})."
        )


def build_window_request(
    *,
    runtime: TriggerRuntime,
    now: datetime,
    job_id: str | None = None,
) -> OAIPMHLoaderEvent:
    """Build a loader event for the next harvesting window.

    Args:
        runtime: Trigger runtime with store and configuration.
        now: Current timestamp for window calculations.
        job_id: Optional job identifier (generated if not provided).

    Returns:
        OAIPMHLoaderEvent to pass to the loader step.

    Raises:
        RuntimeError: If adapter is too far behind (lag check) or no windows ready.
    """
    reporter = WindowReporter(store=runtime.store)

    # First get a preliminary report to determine last_success_end
    preliminary_report = reporter.coverage_report()
    last_success_end = preliminary_report.last_success_end

    # Enforce lag before notifying to avoid repeated alerts when circuit breaker trips
    if runtime.enforce_lag:
        _enforce_lag(
            now,
            last_success_end,
            runtime.max_lag_minutes,
            runtime.adapter_name,
        )

    start_time = _determine_start(
        now,
        last_success_end,
        runtime.window_lookback_days,
    ).astimezone(UTC)
    end_time = now.astimezone(UTC)

    # Generate report for notification using start_time as range_end.
    # This ensures we only report gaps that WON'T be covered by this batch.
    if runtime.notifier:
        notification_report = reporter.coverage_report(range_end=start_time)
        logger.info("Window coverage report", summary=notification_report.summary())
        runtime.notifier.notify_if_gaps(
            report=notification_report,
            job_id=job_id,
            trigger_time=now,
        )
    else:
        # Log the preliminary report summary when no notifier
        logger.info("Window coverage report", summary=preliminary_report.summary())

    if start_time >= end_time:
        raise RuntimeError(
            f"No new windows are ready to process: start_time={start_time.isoformat()} "
            f"last_success_end={last_success_end.isoformat() if last_success_end else 'None'}"
        )

    resolved_job_id = job_id or generate_job_id(now)

    return OAIPMHLoaderEvent(
        job_id=resolved_job_id,
        window=IncrementalWindow(start_time=start_time, end_time=end_time),
        metadata_prefix=runtime.oai_metadata_prefix,
        set_spec=runtime.oai_set_spec,
        max_windows=runtime.max_pending_windows,
        window_minutes=runtime.window_minutes,
    )


def handler(
    event: OAIPMHTriggerEvent,
    runtime: TriggerRuntime,
    execution_context: ExecutionContext | None = None,
) -> OAIPMHLoaderEvent:
    """Execute the trigger step.

    Args:
        event: Trigger event with optional timestamp override.
        runtime: Runtime dependencies and configuration.
        execution_context: Optional logging context.

    Returns:
        OAIPMHLoaderEvent for the loader step.
    """
    setup_logging(execution_context)
    now = event.now or datetime.now(tz=UTC)
    return build_window_request(
        runtime=runtime,
        now=now,
        job_id=event.job_id,
    )


def build_runtime(
    config: OAIPMHRuntimeConfig,
    step_config: TriggerStepConfig | None = None,
) -> TriggerRuntime:
    """Build the trigger runtime from adapter configuration.

    Args:
        config: Adapter-specific runtime configuration.
        step_config: Optional step-specific overrides.

    Returns:
        TriggerRuntime ready for execution.
    """
    cfg = step_config or TriggerStepConfig()
    store = config.build_window_store(use_rest_api_table=cfg.use_rest_api_table)

    # Initialize notifier if chatbot topic is configured
    notifier = None
    if config.config.chatbot_topic_arn:
        sns_client = boto3.client("sns")
        chatbot_notifier = ChatbotNotifier(
            sns_client=sns_client,
            topic_arn=config.config.chatbot_topic_arn,
        )

        # Extract table name for display
        table = store.table
        if callable(getattr(table, "name", None)):
            table_name = ".".join(table.name())
        else:
            # Use _identifier for older pyiceberg versions
            table_name = str(
                getattr(table, "identifier", getattr(table, "_identifier", "unknown"))
            )

        notifier = WindowNotifier(
            chatbot_notifier=chatbot_notifier,
            table_name=table_name,
        )

    return TriggerRuntime(
        store=store,
        notifier=notifier,
        enforce_lag=cfg.enforce_lag,
        window_minutes=cfg.window_minutes or config.config.window_minutes,
        window_lookback_days=cfg.window_lookback_days
        or config.config.window_lookback_days,
        max_lag_minutes=config.config.max_lag_minutes,
        max_pending_windows=config.config.max_pending_windows,
        oai_metadata_prefix=config.config.oai_metadata_prefix,
        oai_set_spec=config.config.oai_set_spec,
        adapter_name=config.config.adapter_name,
    )


def lambda_handler(
    event: dict[str, Any],
    context: Any,
    *,
    config: OAIPMHRuntimeConfig,
) -> dict[str, Any]:
    """Lambda entry point for the trigger step.

    Args:
        event: EventBridge scheduled event payload.
        context: Lambda context object.
        config: Adapter-specific runtime configuration.

    Returns:
        Serialized OAIPMHLoaderEvent for the next step.
    """
    scheduled_event = EventBridgeScheduledEvent.model_validate(event)
    event_time = datetime.fromisoformat(scheduled_event.time.replace("Z", "+00:00"))
    runtime = build_runtime(config)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_trigger",
    )
    loader_event = handler(
        OAIPMHTriggerEvent(
            now=event_time,
            job_id=generate_job_id(event_time),
        ),
        runtime=runtime,
        execution_context=execution_context,
    )
    return loader_event.model_dump(mode="json")


def build_cli_parser(config: OAIPMHRuntimeConfig) -> argparse.ArgumentParser:
    """Build the CLI argument parser with common arguments.

    Args:
        config: Adapter configuration for default values.

    Returns:
        ArgumentParser with common trigger arguments.
    """
    parser = argparse.ArgumentParser(
        description=f"Run the {config.config.adapter_name} trigger step locally"
    )
    parser.add_argument(
        "--at",
        type=str,
        help="ISO8601 timestamp to use instead of now (e.g. 2025-11-17T12:15:00Z)",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables window status catalog instead of a local store",
    )
    parser.add_argument(
        "--enforce-lag",
        action="store_true",
        help="Fail if the latest successful window is older than the configured lag threshold",
    )
    parser.add_argument(
        "--window-minutes",
        type=int,
        help=(
            "Number of minutes per harvesting window request "
            f"(default: {config.config.window_minutes})"
        ),
    )
    parser.add_argument(
        "--lookback-days",
        type=int,
        help=(
            "Number of days to look back when no successful windows exist "
            f"(default: {config.config.window_lookback_days})"
        ),
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Optional job identifier to embed in the request",
    )
    return parser


def run_cli(
    config: OAIPMHRuntimeConfig, args: argparse.Namespace | None = None
) -> None:
    """Run the trigger step from the command line.

    Args:
        config: Adapter-specific runtime configuration.
        args: Parsed arguments (if None, will parse from sys.argv).
    """
    if args is None:
        parser = build_cli_parser(config)
        args = parser.parse_args()

    now = (
        datetime.fromisoformat(args.at.replace("Z", "+00:00"))
        if args.at
        else datetime.now(tz=UTC)
    )
    job_id = args.job_id or generate_job_id(now)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_trigger",
    )
    loader_event = handler(
        OAIPMHTriggerEvent(
            now=now,
            job_id=job_id,
        ),
        runtime=build_runtime(
            config,
            TriggerStepConfig(
                use_rest_api_table=args.use_rest_api_table,
                enforce_lag=args.enforce_lag,
                window_minutes=args.window_minutes,
                window_lookback_days=args.lookback_days,
            ),
        ),
        execution_context=execution_context,
    )
    logger.info("Loader event", event=loader_event.model_dump(mode="json"))
