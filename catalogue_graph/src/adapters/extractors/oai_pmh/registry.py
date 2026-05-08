"""Registry mapping adapter_type strings to OAIPMHRuntimeConfig instances."""

from __future__ import annotations

from functools import cache
from typing import Literal

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig

AdapterType = Literal["axiell", "folio"]

_CONFIGS: dict[str, OAIPMHRuntimeConfig] = {
    "axiell": AXIELL_CONFIG,
    "folio": FOLIO_CONFIG,
}


@cache
def get_config(adapter_type: str) -> OAIPMHRuntimeConfig:
    """Resolve an adapter_type string to its OAIPMHRuntimeConfig.

    Raises:
        ValueError: If the adapter_type is not recognised.
    """
    config = _CONFIGS.get(adapter_type)
    if config is None:
        raise ValueError(
            f"Unknown adapter_type: {adapter_type!r}. "
            f"Expected one of: {sorted(_CONFIGS)}"
        )
    return config
