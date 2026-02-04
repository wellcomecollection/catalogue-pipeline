"""Reloader step for the Axiell adapter.

Thin wrapper around the generic OAI-PMH reloader step.
"""

from __future__ import annotations

from typing import Any

from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.oai_pmh.steps import reloader as base_reloader
from adapters.oai_pmh.steps.reloader import (
    ReloaderRuntime,
)
from adapters.oai_pmh.steps.reloader import (
    ReloaderStepConfig as AxiellAdapterReloaderConfig,
)


def build_runtime(
    config_obj: AxiellAdapterReloaderConfig | None = None,
) -> ReloaderRuntime:
    """Build Axiell reloader runtime from config."""
    cfg = config_obj or AxiellAdapterReloaderConfig()
    return base_reloader.build_runtime(AXIELL_CONFIG, cfg)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """AWS Lambda handler for Axiell reloader."""
    return base_reloader.lambda_handler(
        event,
        context,
        adapter_config=AXIELL_CONFIG,
        pipeline_step="axiell_adapter_reloader",
    )


def main() -> None:
    """Run the Axiell reloader step locally via CLI."""
    base_reloader.run_cli(
        adapter_config=AXIELL_CONFIG,
        pipeline_step="axiell_adapter_reloader",
        description="Reload Axiell harvesting windows to fill coverage gaps",
    )


if __name__ == "__main__":
    main()
