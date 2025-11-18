"""Trigger step for the Axiell adapter.

Computes the next harvesting window, ensuring progress is within the
configured lag tolerance, and emits a WindowRequest payload for the loader.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any

from pydantic import BaseModel

from adapters.axiell import config
from adapters.axiell.models import AxiellAdapterLoaderEvent, AxiellAdapterTriggerEvent
from adapters.axiell.window_status import build_window_store
from adapters.utils.window_store import IcebergWindowStore
from models.events import EventBridgeScheduledEvent


@dataclass
class TriggerRuntime:
    store: IcebergWindowStore


def _window_key(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}_{end.isoformat()}"


def _generate_job_id(timestamp: datetime) -> str:
    return timestamp.astimezone(UTC).strftime("%Y%m%dT%H%M")


def _latest_success_end(store: IcebergWindowStore) -> datetime | None:
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
    store: IcebergWindowStore,
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
    event: AxiellAdapterTriggerEvent, runtime: TriggerRuntime
) -> dict[str, Any]:
    request = build_window_request(
        store=runtime.store,
        now=event.now,
        job_id=event.job_id,
    )
    return request.model_dump(mode="json")


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    scheduled_event = EventBridgeScheduledEvent.model_validate(event)
    event_time = datetime.fromisoformat(scheduled_event.time.replace("Z", "+00:00"))
    request = handler(
        AxiellAdapterTriggerEvent(
            now=event_time,
            job_id=_generate_job_id(event_time),
        ),
        runtime=TriggerRuntime(store=build_window_store(use_rest_api_table=True)),
    )
    return request.model_dump(mode="json")


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
    request = handler(
        AxiellAdapterTriggerEvent(
            now=now,
            job_id=args.job_id,
        ),
        runtime=TriggerRuntime(
            store=build_window_store(use_rest_api_table=args.use_rest_api_table)
        ),
        enforce_lag=args.enforce_lag,
    )
    print(json.dumps(request.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
