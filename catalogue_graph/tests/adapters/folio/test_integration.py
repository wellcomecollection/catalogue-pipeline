"""FOLIO adapter config tests.

Verifies FOLIO-specific configuration values.
For step integration tests, see tests/adapters/oai_pmh/test_adapter_integration.py.
"""

from __future__ import annotations

from adapters.oai_pmh.folio.config import FOLIO_ADAPTER_CONFIG
from adapters.oai_pmh.folio.runtime import FOLIO_CONFIG, FolioRuntimeConfig
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig


class TestFolioConfig:
    def test_folio_config_values(self) -> None:
        cfg = FOLIO_CONFIG.config

        # Adapter identity
        assert cfg.adapter_name == "folio"
        assert cfg.adapter_namespace == "folio"
        assert cfg.pipeline_step_prefix == "folio_adapter"

        # OAI-PMH settings
        assert cfg.oai_metadata_prefix == "marc21_withholdings"
        assert cfg.oai_set_spec is None  # FOLIO harvests all records

        # Config is frozen/immutable
        assert FOLIO_ADAPTER_CONFIG.model_config.get("frozen") is True

        # Inherits from base class
        assert isinstance(FOLIO_CONFIG, OAIPMHRuntimeConfig)
        assert isinstance(FOLIO_CONFIG, FolioRuntimeConfig)
