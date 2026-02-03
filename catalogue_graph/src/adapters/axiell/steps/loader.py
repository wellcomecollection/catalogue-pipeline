"""Loader step for the Axiell adapter.

Thin wrapper around the generic OAI-PMH loader step, configured with
Axiell-specific settings.
"""

from __future__ import annotations

from typing import Any

from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.oai_pmh.models.step_events import OAIPMHLoaderResponse
from adapters.oai_pmh.steps import loader as base_loader
from adapters.oai_pmh.steps.loader import LoaderRuntime
from adapters.oai_pmh.steps.loader import (
    LoaderStepConfig as AxiellAdapterLoaderConfig,
)
from adapters.oai_pmh.steps.loader import build_runtime as _build_runtime
from adapters.oai_pmh.steps.loader import handler as _handler


def build_runtime(
    config_obj: AxiellAdapterLoaderConfig | None = None,
) -> LoaderRuntime:
    """Build runtime using Axiell config (backwards compatible)."""
    return _build_runtime(AXIELL_CONFIG, config_obj)


def handler(
    event: Any,
    runtime: LoaderRuntime,
    execution_context: Any = None,
) -> OAIPMHLoaderResponse:
    """Execute the loader step (backwards-compatible wrapper)."""
    response = _handler(event, runtime, execution_context)
    return OAIPMHLoaderResponse.model_validate(response.model_dump())


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point for the Axiell loader step."""
    return base_loader.lambda_handler(event, context, config=AXIELL_CONFIG)


def main() -> None:
    """CLI entry point for the Axiell loader step."""
    base_loader.run_cli(AXIELL_CONFIG)


if __name__ == "__main__":
    main()
