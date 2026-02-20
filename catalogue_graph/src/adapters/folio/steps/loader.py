"""Loader step for the FOLIO adapter.

Thin wrapper around the generic OAI-PMH loader step, configured with
FOLIO-specific settings.
"""

from __future__ import annotations

from typing import Any

from adapters.folio.runtime import FOLIO_CONFIG
from adapters.oai_pmh.models.step_events import OAIPMHLoaderResponse
from adapters.oai_pmh.steps import loader as base_loader
from adapters.oai_pmh.steps.loader import LoaderRuntime, LoaderStepConfig
from adapters.oai_pmh.steps.loader import build_runtime as _build_runtime
from adapters.oai_pmh.steps.loader import handler as _handler


def build_runtime(
    config_obj: LoaderStepConfig | None = None,
) -> LoaderRuntime:
    """Build runtime using FOLIO config."""
    return _build_runtime(FOLIO_CONFIG, config_obj)


def handler(
    event: Any,
    runtime: LoaderRuntime,
    execution_context: Any = None,
) -> OAIPMHLoaderResponse:
    """Execute the loader step."""
    response = _handler(event, runtime, execution_context)
    return OAIPMHLoaderResponse.model_validate(response.model_dump())


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda entry point for the FOLIO loader step."""
    return base_loader.lambda_handler(event, context, config=FOLIO_CONFIG)


def main() -> None:
    """CLI entry point for the FOLIO loader step."""
    base_loader.run_cli(FOLIO_CONFIG)


if __name__ == "__main__":
    main()
