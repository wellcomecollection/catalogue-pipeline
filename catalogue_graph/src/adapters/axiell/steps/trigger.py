"""Trigger step for the Axiell adapter.

Thin wrapper around the generic OAI-PMH trigger step, configured with
Axiell-specific settings.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field

from adapters.axiell import config
from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHTriggerEvent
from adapters.oai_pmh.steps import trigger as base_trigger
from adapters.oai_pmh.steps.trigger import TriggerRuntime, TriggerStepConfig
from adapters.oai_pmh.steps.trigger import build_runtime as _build_runtime
from adapters.oai_pmh.steps.trigger import handler as _handler
from adapters.utils.window_notifier import WindowNotifier
from adapters.utils.window_store import WindowStore
from utils.logger import ExecutionContext


class AxiellAdapterTriggerConfig(BaseModel):
    """Configuration for Axiell trigger step."""

    use_rest_api_table: bool = True
    enforce_lag: bool = True
    window_minutes: int = Field(default_factory=lambda: config.WINDOW_MINUTES)
    window_lookback_days: int = Field(
        default_factory=lambda: config.WINDOW_LOOKBACK_DAYS
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
) -> OAIPMHLoaderEvent:
    """Build a loader event for the Axiell adapter."""
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
        adapter_name=AXIELL_CONFIG.adapter_name,
    )
    generic_event = base_trigger.build_window_request(
        runtime=runtime,
        now=now,
        job_id=job_id,
    )
    # Convert to Axiell-specific type for backwards compatibility
    return OAIPMHLoaderEvent.model_validate(generic_event.model_dump())


def handler(
    event: OAIPMHTriggerEvent,
    runtime: TriggerRuntime,
    execution_context: ExecutionContext | None = None,
) -> OAIPMHLoaderEvent:
    """Execute the Axiell trigger step."""
    generic_event = _handler(event, runtime, execution_context)
    return OAIPMHLoaderEvent.model_validate(generic_event.model_dump())


def build_runtime(
    config_obj: AxiellAdapterTriggerConfig | None = None,
) -> TriggerRuntime:
    """Build runtime for the Axiell trigger step."""
    cfg = config_obj or AxiellAdapterTriggerConfig()
    return _build_runtime(
        AXIELL_CONFIG,
        TriggerStepConfig(
            use_rest_api_table=cfg.use_rest_api_table,
            enforce_lag=cfg.enforce_lag,
            window_minutes=cfg.window_minutes,
            window_lookback_days=cfg.window_lookback_days,
        ),
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point for the Axiell trigger step."""
    return base_trigger.lambda_handler(event, context, config=AXIELL_CONFIG)


def main() -> None:
    """CLI entry point for the Axiell trigger step."""
    base_trigger.run_cli(AXIELL_CONFIG)


if __name__ == "__main__":
    main()
