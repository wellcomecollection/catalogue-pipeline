"""Registry mapping adapter_type strings to OAIPMHRuntimeConfig instances."""

from __future__ import annotations

from typing import Literal

from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig

AdapterType = Literal["axiell", "folio"]

_CONFIGS: dict[str, OAIPMHRuntimeConfig] | None = None


def _load_configs() -> dict[str, OAIPMHRuntimeConfig]:
    # Lazy imports to avoid heavyweight module-level side effects
    # (SSM lookups, HTTP client construction) when not needed.
    from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
    from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG

    return {
        "axiell": AXIELL_CONFIG,
        "folio": FOLIO_CONFIG,
    }


def get_config(adapter_type: str) -> OAIPMHRuntimeConfig:
    """Resolve an adapter_type string to its OAIPMHRuntimeConfig.

    Raises:
        ValueError: If the adapter_type is not recognised.
    """
    global _CONFIGS
    if _CONFIGS is None:
        _CONFIGS = _load_configs()

    config = _CONFIGS.get(adapter_type)
    if config is None:
        raise ValueError(
            f"Unknown adapter_type: {adapter_type!r}. "
            f"Expected one of: {sorted(_CONFIGS)}"
        )
    return config
