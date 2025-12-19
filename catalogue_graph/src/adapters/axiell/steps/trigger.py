"""Trigger step for the Axiell adapter.

Computes the next harvesting window, ensuring progress is within the
configured lag tolerance, and emits a WindowRequest payload for the loader.
"""

from __future__ import annotations

import argparse
import json
import logging
from datetime import UTC, datetime, timedelta
from typing import Any

import boto3
from pydantic import BaseModel, ConfigDict, Field

from adapters.axiell import config, helpers
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    AxiellAdapterTriggerEvent,
)
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_reporter import WindowReporter
from adapters.utils.window_store import WindowStore
from clients.chatbot_notifier import ChatbotNotifier
from models.events import EventBridgeScheduledEvent, IncrementalWindow

logging.basicConfig(level=logging.INFO)


class AxiellAdapterTriggerConfig(BaseModel):
    use_rest_api_table: bool = True
    enforce_lag: bool = True
    window_minutes: int = Field(default_factory=lambda: config.WINDOW_MINUTES)
    window_lookback_days: int = Field(
        default_factory=lambda: config.WINDOW_LOOKBACK_DAYS
    )


class TriggerRuntime(BaseModel):
    store: WindowStore
    notifier: WindowNotifier | None = None
    enforce_lag: bool = True
    window_minutes: int = config.WINDOW_MINUTES
    window_lookback_days: int = config.WINDOW_LOOKBACK_DAYS

    model_config = ConfigDict(arbitrary_types_allowed=True)


def _generate_job_id(timestamp: datetime) -> str:
    return timestamp.astimezone(UTC).strftime("%Y%m%dT%H%M")


def _determine_start(
    now: datetime, last_success_end: datetime | None, window_lookback_days: int
) -> datetime:
    if last_success_end is not None:
        return last_success_end
    return now - timedelta(days=window_lookback_days)


def _enforce_lag(now: datetime, last_success_end: datetime | None) -> None:
    if last_success_end is None:
        return
    lag_minutes = (now - last_success_end).total_seconds() / 60
    if lag_minutes > config.MAX_LAG_MINUTES:
        raise RuntimeError(
            "Axiell adapter is too far behind: last successful window "
            f"ended {lag_minutes:.1f} minutes ago (limit={config.MAX_LAG_MINUTES})."
        )


def build_window_request(
    *,
    store: WindowStore,
    now: datetime,
    enforce_lag: bool = True,
    job_id: str | None = None,
    window_minutes: int | None = None,
    window_lookback_days: int | None = None,
    notifier: WindowNotifier | None = None,
) -> AxiellAdapterLoaderEvent:
    reporter = WindowReporter(store=store)

    # First get a preliminary report to determine last_success_end
    preliminary_report = reporter.coverage_report()
    last_success_end = preliminary_report.last_success_end

    # Enforce lag before notifying to avoid repeated alerts when circuit breaker trips
    if enforce_lag:
        _enforce_lag(now, last_success_end)

    resolved_window_lookback = window_lookback_days or config.WINDOW_LOOKBACK_DAYS
    resolved_window_minutes = window_minutes or config.WINDOW_MINUTES

    start_time = _determine_start(
        now,
        last_success_end,
        resolved_window_lookback,
    ).astimezone(UTC)
    end_time = now.astimezone(UTC)

    # Generate report for notification using start_time as range_end.
    # This ensures we only report gaps that WON'T be covered by this batch.
    # The batch will process from start_time to end_time, so any gaps
    # before start_time are true historical gaps that need attention.
    if notifier:
        notification_report = reporter.coverage_report(range_end=start_time)
        logging.info(notification_report.summary())
        notifier.notify_if_gaps(
            report=notification_report,
            job_id=job_id,
            trigger_time=now,
        )
    else:
        # Log the preliminary report summary when no notifier
        logging.info(preliminary_report.summary())

    if start_time >= end_time:
        raise RuntimeError(
            f"No new windows are ready to process: start_time={start_time.isoformat()} "
            f"last_success_end={last_success_end.isoformat() if last_success_end else 'None'}"
        )

    max_windows = config.MAX_PENDING_WINDOWS
    resolved_job_id = job_id or _generate_job_id(now)

    loader_event = AxiellAdapterLoaderEvent(
        job_id=resolved_job_id,
        window=IncrementalWindow(start_time=start_time, end_time=end_time),
        metadata_prefix=config.OAI_METADATA_PREFIX,
        set_spec=config.OAI_SET_SPEC,
        max_windows=max_windows,
        window_minutes=resolved_window_minutes,
    )

    return loader_event


def handler(
    event: AxiellAdapterTriggerEvent,
    runtime: TriggerRuntime,
) -> AxiellAdapterLoaderEvent:
    now = event.now or datetime.now(tz=UTC)
    return build_window_request(
        store=runtime.store,
        now=now,
        enforce_lag=runtime.enforce_lag,
        job_id=event.job_id,
        window_minutes=runtime.window_minutes,
        window_lookback_days=runtime.window_lookback_days,
        notifier=runtime.notifier,
    )


def build_runtime(
    config_obj: AxiellAdapterTriggerConfig | None = None,
) -> TriggerRuntime:
    cfg = config_obj or AxiellAdapterTriggerConfig()
    store = helpers.build_window_store(use_rest_api_table=cfg.use_rest_api_table)

    # Initialize notifier if CHATBOT_TOPIC_ARN is configured
    notifier = None
    if config.CHATBOT_TOPIC_ARN:
        sns_client = boto3.client("sns")
        chatbot_notifier = ChatbotNotifier(
            sns_client=sns_client,
            topic_arn=config.CHATBOT_TOPIC_ARN,
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
        window_minutes=cfg.window_minutes,
        window_lookback_days=cfg.window_lookback_days,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    scheduled_event = EventBridgeScheduledEvent.model_validate(event)
    event_time = datetime.fromisoformat(scheduled_event.time.replace("Z", "+00:00"))
    runtime = build_runtime()
    loader_event = handler(
        AxiellAdapterTriggerEvent(
            now=event_time,
            job_id=_generate_job_id(event_time),
        ),
        runtime=runtime,
    )
    return loader_event.model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Axiell trigger step locally")
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
            f"(default: {config.WINDOW_MINUTES})"
        ),
    )
    parser.add_argument(
        "--lookback-days",
        type=int,
        help=(
            "Number of days to look back when no successful windows exist "
            f"(default: {config.WINDOW_LOOKBACK_DAYS})"
        ),
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="Optional job identifier to embed in the request",
    )
    args = parser.parse_args()
    now = (
        datetime.fromisoformat(args.at.replace("Z", "+00:00"))
        if args.at
        else datetime.now(tz=UTC)
    )
    job_id = args.job_id or _generate_job_id(now)
    loader_event = handler(
        AxiellAdapterTriggerEvent(
            now=now,
            job_id=job_id,
        ),
        runtime=build_runtime(
            AxiellAdapterTriggerConfig(
                use_rest_api_table=args.use_rest_api_table,
                enforce_lag=args.enforce_lag,
                window_minutes=args.window_minutes or config.WINDOW_MINUTES,
                window_lookback_days=args.lookback_days or config.WINDOW_LOOKBACK_DAYS,
            )
        ),
    )
    print(json.dumps(loader_event.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
