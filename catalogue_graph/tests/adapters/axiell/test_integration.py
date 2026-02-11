"""Axiell adapter integration tests.

These tests verify the Axiell adapter happy paths using local Iceberg tables.
For core OAI-PMH logic tests, see tests/adapters/oai_pmh/.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.axiell.runtime import AXIELL_CONFIG, AxiellRuntimeConfig
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.utils.window_store import WindowStore
from tests.adapters.oai_pmh.conftest import create_window_row, populate_window_store


class TestAxiellConfig:
    """Test that Axiell adapter has correct configuration."""

    def test_axiell_config_values(self) -> None:
        """Verify all Axiell-specific configuration is correct."""
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


class TestAxiellTriggerIntegration:
    """Integration tests for the Axiell trigger step."""

    def test_trigger_builds_valid_loader_event(
        self,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test trigger step produces a valid loader event using local table."""
        from adapters.axiell.steps import trigger

        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = WindowStore(temporary_window_status_table)

        # Build a window request with no prior history
        request = trigger.build_window_request(
            store=store,
            now=now,
            window_lookback_days=1,
        )

        # Verify Axiell-specific values
        assert request.metadata_prefix == "oai_marcxml"
        assert request.set_spec == "collect"
        assert request.job_id == "20251117T1200"
        assert request.window.start_time == now - timedelta(days=1)
        assert request.window.end_time == now

    def test_trigger_resumes_from_last_successful_window(
        self,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test trigger resumes from last successful window in local table."""
        from adapters.axiell.steps import trigger

        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        last_success_end = now - timedelta(minutes=30)

        store = populate_window_store(
            temporary_window_status_table,
            [
                create_window_row(
                    last_success_end - timedelta(minutes=15), last_success_end
                )
            ],
        )

        request = trigger.build_window_request(store=store, now=now)

        assert request.window.start_time == last_success_end
        assert request.window.end_time == now


class TestAxiellLoaderIntegration:
    """Integration tests for the Axiell loader step."""

    def test_loader_runtime_builds_with_local_table(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_table: IcebergTable,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test loader runtime builds correctly with local tables."""
        from adapters.axiell.steps import loader
        from adapters.oai_pmh.steps.loader import LoaderRuntime, LoaderStepConfig
        from adapters.utils.window_store import WindowStore
        from tests.adapters.oai_pmh.conftest import StubOAIClient

        # Wire up local tables
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_adapter_table",
            lambda use_rest_api_table: temporary_table,
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_window_store",
            lambda use_rest_api_table: WindowStore(temporary_window_status_table),
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_oai_client",
            lambda http_client=None: StubOAIClient(),
        )

        config_obj = LoaderStepConfig(use_rest_api_table=False, window_minutes=15)
        runtime = loader.build_runtime(config_obj)

        assert isinstance(runtime, LoaderRuntime)
        assert runtime.adapter_name == "axiell"
        assert runtime.adapter_namespace == "axiell"
        assert runtime.window_generator.window_minutes == 15


class TestAxiellReloaderIntegration:
    """Integration tests for the Axiell reloader step."""

    def test_reloader_runtime_builds_with_local_table(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_table: IcebergTable,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test reloader runtime builds correctly with local tables."""
        from adapters.axiell.steps import reloader
        from adapters.axiell.steps.reloader import AxiellAdapterReloaderConfig
        from adapters.oai_pmh.steps.reloader import ReloaderRuntime
        from adapters.utils.window_store import WindowStore
        from tests.adapters.oai_pmh.conftest import StubOAIClient

        # Wire up local tables
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_adapter_table",
            lambda use_rest_api_table: temporary_table,
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_window_store",
            lambda use_rest_api_table: WindowStore(temporary_window_status_table),
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_oai_client",
            lambda http_client=None: StubOAIClient(),
        )

        config_obj = AxiellAdapterReloaderConfig(
            use_rest_api_table=False, window_minutes=15
        )
        runtime = reloader.build_runtime(config_obj)

        assert isinstance(runtime, ReloaderRuntime)
        assert runtime.adapter_config is AXIELL_CONFIG
