"""OAI-PMH adapter integration tests (parameterized over all adapters).

These tests verify that the unified OAI-PMH step entrypoints work correctly
with each adapter configuration. Runs once per adapter (Axiell, FOLIO).

For adapter-specific config value assertions, see each adapter's own
test_integration.py.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.sources.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.sources.oai_pmh.steps import loader as oai_loader
from adapters.sources.oai_pmh.steps import reloader as oai_reloader
from adapters.sources.oai_pmh.steps.loader import LoaderRuntime, LoaderStepConfig
from adapters.sources.oai_pmh.steps.reloader import ReloaderRuntime, ReloaderStepConfig
from adapters.sources.oai_pmh.steps.trigger import TriggerRuntime, build_window_request
from adapters.utils.window_store import WindowStore
from tests.adapters.sources.oai_pmh.conftest import (
    StubOAIClient,
    create_window_row,
    populate_window_store,
)


class TestTriggerIntegration:
    def test_trigger_builds_valid_loader_event(
        self,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        cfg = adapter_runtime_config.config
        now = datetime(2025, 11, 17, 12, 0, tzinfo=UTC)
        store = WindowStore(temporary_window_status_table)

        runtime = TriggerRuntime(
            store=store,
            notifier=None,
            enforce_lag=False,
            window_minutes=cfg.window_minutes,
            window_lookback_days=1,
            max_lag_minutes=cfg.max_lag_minutes,
            max_pending_windows=cfg.max_pending_windows,
            oai_metadata_prefix=cfg.oai_metadata_prefix,
            oai_set_spec=cfg.oai_set_spec,
            adapter_name=cfg.adapter_name,
        )
        request = build_window_request(runtime=runtime, now=now)

        assert request.metadata_prefix == cfg.oai_metadata_prefix
        assert request.set_spec == cfg.oai_set_spec
        assert request.job_id == "20251117T1200"
        assert request.window.start_time == now - timedelta(days=1)
        assert request.window.end_time == now

    def test_trigger_resumes_from_last_successful_window(
        self,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        cfg = adapter_runtime_config.config
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

        runtime = TriggerRuntime(
            store=store,
            notifier=None,
            enforce_lag=False,
            window_minutes=cfg.window_minutes,
            window_lookback_days=cfg.window_lookback_days,
            max_lag_minutes=cfg.max_lag_minutes,
            max_pending_windows=cfg.max_pending_windows,
            oai_metadata_prefix=cfg.oai_metadata_prefix,
            oai_set_spec=cfg.oai_set_spec,
            adapter_name=cfg.adapter_name,
        )
        request = build_window_request(runtime=runtime, now=now)

        assert request.window.start_time == last_success_end
        assert request.window.end_time == now


class TestLoaderIntegration:
    def test_loader_runtime_builds_with_local_table(
        self,
        monkeypatch: pytest.MonkeyPatch,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        temporary_table: IcebergTable,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        cfg = adapter_runtime_config.config
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_adapter_table",
            lambda use_rest_api_table: temporary_table,
        )
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_window_store",
            lambda use_rest_api_table: WindowStore(temporary_window_status_table),
        )
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_oai_client",
            lambda http_client=None: StubOAIClient(),
        )

        config_obj = LoaderStepConfig(use_rest_api_table=False, window_minutes=15)
        runtime = oai_loader.build_runtime(adapter_runtime_config, config_obj)

        assert isinstance(runtime, LoaderRuntime)
        assert runtime.adapter_name == cfg.adapter_name
        assert runtime.adapter_namespace == cfg.adapter_namespace
        assert runtime.window_generator.window_minutes == 15


class TestReloaderIntegration:
    def test_reloader_runtime_builds_with_local_table(
        self,
        monkeypatch: pytest.MonkeyPatch,
        adapter_runtime_config: OAIPMHRuntimeConfig,
        temporary_table: IcebergTable,
        temporary_window_status_table: IcebergTable,
    ) -> None:
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_adapter_table",
            lambda use_rest_api_table: temporary_table,
        )
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_window_store",
            lambda use_rest_api_table: WindowStore(temporary_window_status_table),
        )
        monkeypatch.setattr(
            adapter_runtime_config,
            "build_oai_client",
            lambda http_client=None: StubOAIClient(),
        )

        config_obj = ReloaderStepConfig(use_rest_api_table=False, window_minutes=15)
        runtime = oai_reloader.build_runtime(adapter_runtime_config, config_obj)

        assert isinstance(runtime, ReloaderRuntime)
        assert runtime.adapter_config is adapter_runtime_config
