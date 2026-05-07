"""Axiell adapter config tests.

Verifies Axiell-specific configuration values.
For step integration tests, see tests/adapters/oai_pmh/test_adapter_integration.py.
"""

from __future__ import annotations

from adapters.sources.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.sources.oai_pmh.axiell.runtime import AXIELL_CONFIG, AxiellRuntimeConfig
from adapters.sources.oai_pmh.runtime import OAIPMHRuntimeConfig


class TestAxiellConfig:
    def test_axiell_config_values(self) -> None:
        cfg = AXIELL_CONFIG.config

        # Adapter identity
        assert cfg.adapter_name == "axiell"
        assert cfg.adapter_namespace == "axiell"
        assert cfg.pipeline_step_prefix == "axiell_adapter"

        # OAI-PMH settings
        assert cfg.oai_metadata_prefix == "oai_marcxml"
        assert cfg.oai_set_spec == "collect"

        # Config is frozen/immutable
        assert AXIELL_ADAPTER_CONFIG.model_config.get("frozen") is True

        # Inherits from base class
        assert isinstance(AXIELL_CONFIG, OAIPMHRuntimeConfig)
        assert isinstance(AXIELL_CONFIG, AxiellRuntimeConfig)
