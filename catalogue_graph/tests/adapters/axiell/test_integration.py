"""Axiell adapter integration tests.

These tests verify that the Axiell adapter correctly configures and uses
the underlying OAI-PMH implementation. For core OAI-PMH logic tests,
see tests/adapters/oai_pmh/.
"""

from __future__ import annotations

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.axiell.runtime import AXIELL_CONFIG, AxiellRuntimeConfig
from adapters.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig


class TestAxiellConfig:
    """Tests for Axiell adapter configuration."""

    def test_adapter_name_is_axiell(self) -> None:
        """Test that the adapter name is correctly set."""
        assert AXIELL_CONFIG.config.adapter_name == "axiell"

    def test_adapter_namespace_is_axiell(self) -> None:
        """Test that the adapter namespace is correctly set."""
        assert AXIELL_CONFIG.config.adapter_namespace == "axiell"

    def test_metadata_prefix_is_oai_marcxml(self) -> None:
        """Test that the metadata prefix is oai_marcxml."""
        assert AXIELL_CONFIG.config.oai_metadata_prefix == "oai_marcxml"

    def test_set_spec_is_collect(self) -> None:
        """Test that the set spec is 'collect'."""
        assert AXIELL_CONFIG.config.oai_set_spec == "collect"

    def test_adapter_config_is_frozen(self) -> None:
        """Test that the adapter config is immutable."""
        assert AXIELL_ADAPTER_CONFIG.model_config.get("frozen") is True


class TestAxiellRuntimeConfig:
    """Tests for Axiell adapter runtime configuration."""

    def test_extends_oai_pmh_runtime_config(self) -> None:
        """Test that AxiellRuntimeConfig extends OAIPMHRuntimeConfig."""
        assert isinstance(AXIELL_CONFIG, OAIPMHRuntimeConfig)
        assert isinstance(AXIELL_CONFIG, AxiellRuntimeConfig)

    def test_accepts_custom_config(self) -> None:
        """Test that AxiellRuntimeConfig accepts a custom OAIPMHAdapterConfig."""
        custom_config = OAIPMHAdapterConfig(
            adapter_name="axiell-custom",
            adapter_namespace="axiell_custom",
            pipeline_step_prefix="axiell_custom_adapter",
            window_minutes=30,
            window_lookback_days=14,
            max_lag_minutes=720,
            max_pending_windows=100,
            oai_metadata_prefix="oai_dc",
            oai_set_spec="test-set",
            chatbot_topic_arn=None,
            s3_tables_bucket="test-bucket",
            rest_api_table_name="test_table",
            rest_api_namespace="test_ns",
            window_status_table="window_status",
            window_status_namespace="test_window_ns",
            aws_region="us-east-1",
            aws_account_id="123456789012",
            local_db_name="test_db",
            local_table_name="test_local",
            local_namespace="test_local_ns",
            local_window_status_db_name="test_window_db",
            local_window_status_table="window_status",
            local_window_status_namespace="test_window_ns",
        )

        runtime = AxiellRuntimeConfig(custom_config)

        assert runtime.config.adapter_name == "axiell-custom"
        assert runtime.config.window_minutes == 30

    def test_config_singleton_is_consistent(self) -> None:
        """Test that AXIELL_CONFIG singleton has consistent values."""
        from adapters.axiell.runtime import AXIELL_CONFIG as config_import_1
        from adapters.axiell.runtime import AXIELL_CONFIG as config_import_2

        assert config_import_1 is config_import_2


class TestAxiellLoaderStep:
    """Tests for Axiell loader step integration."""

    def test_build_runtime_returns_loader_runtime(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_table: IcebergTable,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test that build_runtime returns a properly configured LoaderRuntime."""
        from adapters.axiell.steps import loader
        from adapters.oai_pmh.steps.loader import LoaderRuntime, LoaderStepConfig
        from adapters.utils.window_store import WindowStore
        from tests.adapters.oai_pmh.conftest import StubOAIClient

        def mock_build_adapter_table(use_rest_api_table: bool):  # type: ignore[no-untyped-def]
            return temporary_table

        def mock_build_window_store(use_rest_api_table: bool) -> WindowStore:
            return WindowStore(temporary_window_status_table)

        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_adapter_table",
            lambda use_rest_api_table: mock_build_adapter_table(use_rest_api_table),
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_window_store",
            lambda use_rest_api_table: mock_build_window_store(use_rest_api_table),
        )
        monkeypatch.setattr(
            AXIELL_CONFIG,
            "build_oai_client",
            lambda http_client=None: StubOAIClient(),
        )

        config_obj = LoaderStepConfig(use_rest_api_table=False, window_minutes=30)
        runtime = loader.build_runtime(config_obj)

        assert isinstance(runtime, LoaderRuntime)
        assert runtime.adapter_name == "axiell"
        assert runtime.adapter_namespace == "axiell"
        assert runtime.window_generator.window_minutes == 30


class TestAxiellTriggerStep:
    """Tests for Axiell trigger step integration."""

    def test_lambda_handler_uses_axiell_config(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test that lambda_handler uses Axiell configuration."""
        from adapters.axiell.steps import trigger
        from adapters.utils.window_store import WindowStore
        from tests.adapters.oai_pmh.conftest import populate_window_store

        stub_store = populate_window_store(temporary_window_status_table, [])
        captured: dict[str, bool] = {}

        def fake_build_window_store(*, use_rest_api_table: bool) -> WindowStore:
            captured["flag"] = use_rest_api_table
            return stub_store

        def fake_handler(event, runtime, execution_context=None):  # type: ignore[no-untyped-def]
            from datetime import UTC, datetime, timedelta

            from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent
            from models.incremental_window import IncrementalWindow

            now = event.now or datetime.now(tz=UTC)
            return OAIPMHLoaderEvent(
                job_id=event.job_id,
                window=IncrementalWindow(
                    start_time=now - timedelta(minutes=15),
                    end_time=now,
                ),
                metadata_prefix="oai_marcxml",
                set_spec="collect",
            )

        monkeypatch.setattr(
            AXIELL_CONFIG, "build_window_store", fake_build_window_store
        )
        monkeypatch.setattr(trigger, "handler", fake_handler)

        trigger.lambda_handler({"time": "2025-11-17T12:00:00Z"}, context=None)

        assert captured["flag"] is True


class TestAxiellReloaderStep:
    """Tests for Axiell reloader step integration."""

    def test_build_runtime_uses_axiell_config(
        self,
        monkeypatch: pytest.MonkeyPatch,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        """Test that build_runtime uses Axiell configuration."""
        from types import SimpleNamespace
        from typing import Any, cast
        from unittest.mock import MagicMock

        from adapters.axiell.steps import reloader
        from adapters.axiell.steps.reloader import AxiellAdapterReloaderConfig
        from adapters.oai_pmh.steps.loader import LoaderRuntime, LoaderStepConfig
        from adapters.oai_pmh.steps.reloader import ReloaderRuntime
        from adapters.utils.adapter_store import AdapterStore
        from adapters.utils.window_store import WindowStore

        captured_config: dict[str, Any] = {}

        def mock_build_window_store(use_rest_api_table: bool) -> WindowStore:
            captured_config["use_rest_api_table"] = use_rest_api_table
            return WindowStore(temporary_window_status_table)

        def mock_build_loader_runtime(
            runtime_config: Any,
            config_obj: LoaderStepConfig | None = None,
        ) -> LoaderRuntime:
            captured_config["runtime_config"] = runtime_config
            captured_config["loader_config"] = config_obj
            return LoaderRuntime.model_construct(
                store=cast(WindowStore, SimpleNamespace()),
                table_client=cast(AdapterStore, SimpleNamespace()),
                oai_client=MagicMock(),
                window_generator=SimpleNamespace(window_minutes=60),
            )

        monkeypatch.setattr(
            "adapters.axiell.runtime.AXIELL_CONFIG.build_window_store",
            lambda use_rest_api_table: mock_build_window_store(use_rest_api_table),
        )
        monkeypatch.setattr(
            "adapters.oai_pmh.steps.reloader._build_loader_runtime",
            mock_build_loader_runtime,
        )

        config_obj = AxiellAdapterReloaderConfig(
            use_rest_api_table=True, window_minutes=60
        )
        runtime = reloader.build_runtime(config_obj)

        assert captured_config["use_rest_api_table"] is True
        assert isinstance(runtime, ReloaderRuntime)
        assert runtime.adapter_config is AXIELL_CONFIG
