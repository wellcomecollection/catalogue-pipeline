"""Reloader step for the FOLIO adapter.

Thin wrapper around the generic OAI-PMH reloader step.
"""

from __future__ import annotations

from typing import Any

from adapters.folio.runtime import FOLIO_CONFIG
from adapters.oai_pmh.steps import reloader as base_reloader
from adapters.oai_pmh.steps.reloader import ReloaderRuntime
from adapters.oai_pmh.steps.reloader import (
    ReloaderStepConfig as FolioAdapterReloaderConfig,
)


def build_runtime(
    config_obj: FolioAdapterReloaderConfig | None = None,
) -> ReloaderRuntime:
    """Build FOLIO reloader runtime from config."""
    cfg = config_obj or FolioAdapterReloaderConfig()
    return base_reloader.build_runtime(FOLIO_CONFIG, cfg)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """AWS Lambda handler for FOLIO reloader."""
    return base_reloader.lambda_handler(
        event,
        context,
        adapter_config=FOLIO_CONFIG,
        pipeline_step="folio_adapter_reloader",
    )


def main() -> None:
    """Run the FOLIO reloader step locally via CLI."""
    base_reloader.run_cli(
        adapter_config=FOLIO_CONFIG,
        pipeline_step="folio_adapter_reloader",
        description="Reload FOLIO harvesting windows to fill coverage gaps",
    )


if __name__ == "__main__":
    main()
