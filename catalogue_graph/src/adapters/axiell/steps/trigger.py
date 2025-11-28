"""Trigger step for the Axiell adapter.

Computes the next harvesting window, ensuring progress is within the
configured lag tolerance, and emits a WindowRequest payload for the loader.
"""

from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime, timedelta
from typing import Any

from pydantic import BaseModel, ConfigDict

from adapters.axiell import config, helpers
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    AxiellAdapterTriggerEvent,
)
from adapters.utils.window_store import WindowStore
from models.events import EventBridgeScheduledEvent


class AxiellAdapterTriggerConfig(BaseModel):
    use_rest_api_table: bool = True
    enforce_lag: bool = True


class TriggerRuntime(BaseModel):
    store: WindowStore
    enforce_lag: bool = True

    model_config = ConfigDict(arbitrary_types_allowed=True)


def _window_key(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}_{end.isoformat()}"


def _generate_job_id(timestamp: datetime) -> str:
    return timestamp.astimezone(UTC).strftime("%Y%m%dT%H%M")


def _latest_success_end(store: WindowStore) -> datetime | None:
    rows = store.list_by_state("success")
    if not rows:
        return None
    latest = max(rows, key=lambda row: row["window_end"])
    end_time = latest.get("window_end")
    if not isinstance(end_time, datetime):
        return None
    return end_time.astimezone(UTC)


def _determine_start(now: datetime, last_success_end: datetime | None) -> datetime:
    if last_success_end is not None:
        return last_success_end
    return now - timedelta(days=config.WINDOW_LOOKBACK_DAYS)


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
) -> AxiellAdapterLoaderEvent:
    last_success_end = _latest_success_end(store)

    if enforce_lag:
        _enforce_lag(now, last_success_end)

    start_time = _determine_start(now, last_success_end).astimezone(UTC)
    end_time = now.astimezone(UTC)

    if start_time >= end_time:
        raise RuntimeError("No new windows are ready: computed start >= end.")

    max_windows = config.MAX_PENDING_WINDOWS
    resolved_job_id = job_id or _generate_job_id(now)

    loader_event = AxiellAdapterLoaderEvent(
        job_id=resolved_job_id,
        window_key=_window_key(start_time, end_time),
        window_start=start_time,
        window_end=end_time,
        metadata_prefix=config.OAI_METADATA_PREFIX,
        set_spec=config.OAI_SET_SPEC,
        max_windows=max_windows,
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
    )


def build_runtime(
    config_obj: AxiellAdapterTriggerConfig | None = None,
) -> TriggerRuntime:
    cfg = config_obj or AxiellAdapterTriggerConfig()
    store = helpers.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    return TriggerRuntime(store=store, enforce_lag=cfg.enforce_lag)


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
                use_rest_api_table=args.use_rest_api_table, enforce_lag=args.enforce_lag
            )
        ),
    )
    print(json.dumps(loader_event.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
