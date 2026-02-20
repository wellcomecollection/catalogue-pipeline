"""Trigger step for the FOLIO adapter.

Thin wrapper around the generic OAI-PMH trigger step, configured with
FOLIO-specific settings.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from adapters.folio import config
from adapters.folio.runtime import FOLIO_CONFIG
from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHTriggerEvent
from adapters.oai_pmh.steps import trigger as base_trigger
from adapters.oai_pmh.steps.trigger import TriggerRuntime, TriggerStepConfig
from adapters.oai_pmh.steps.trigger import build_runtime as _build_runtime
from adapters.oai_pmh.steps.trigger import handler as _handler
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_store import WindowStore
from utils.logger import ExecutionContext


def build_window_request(
    *,
    store: WindowStore,
    now: datetime,
    enforce_lag: bool = True,
    job_id: str | None = None,
    window_minutes: int | None = None,
    window_lookback_days: int | None = None,
    notifier: WindowNotifier | None = None,
) -> OAIPMHLoaderEvent:
    """Build a loader event for the FOLIO adapter."""
    runtime = TriggerRuntime(
        store=store,
        notifier=notifier,
        enforce_lag=enforce_lag,
        window_minutes=window_minutes or config.WINDOW_MINUTES,
        window_lookback_days=window_lookback_days or config.WINDOW_LOOKBACK_DAYS,
        max_lag_minutes=config.MAX_LAG_MINUTES,
        max_pending_windows=config.MAX_PENDING_WINDOWS,
        oai_metadata_prefix=config.OAI_METADATA_PREFIX,
        oai_set_spec=config.OAI_SET_SPEC,
        adapter_name=FOLIO_CONFIG.config.adapter_name,
    )
    generic_event = base_trigger.build_window_request(
        runtime=runtime,
        now=now,
        job_id=job_id,
    )
    return OAIPMHLoaderEvent.model_validate(generic_event.model_dump())


def handler(
    event: OAIPMHTriggerEvent,
    runtime: TriggerRuntime,
    execution_context: ExecutionContext | None = None,
) -> OAIPMHLoaderEvent:
    """Execute the FOLIO trigger step."""
    generic_event = _handler(event, runtime, execution_context)
    return OAIPMHLoaderEvent.model_validate(generic_event.model_dump())


def build_runtime(
    config_obj: TriggerStepConfig | None = None,
) -> TriggerRuntime:
    """Build runtime for the FOLIO trigger step."""
    cfg = config_obj or TriggerStepConfig()
    return _build_runtime(FOLIO_CONFIG, cfg)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point for the FOLIO trigger step."""
    return base_trigger.lambda_handler(event, context, config=FOLIO_CONFIG)


def main() -> None:
    """CLI entry point for the FOLIO trigger step."""
    base_trigger.run_cli(FOLIO_CONFIG)


if __name__ == "__main__":
    main()
