"""Pytest fixtures for OAI-PMH adapter tests.

This module provides parameterized fixtures that test the shared OAI-PMH
implementation with multiple adapter configurations (Axiell, FOLIO).

Fixtures:
    adapter_runtime_config: Parameterized runtime config for both adapters
    adapter_namespace: The namespace string for the current adapter
    adapter_name: The name of the current adapter
    oai_metadata_prefix: The OAI metadata prefix for the current adapter
    oai_set_spec: The OAI set spec for the current adapter
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest
from oai_pmh_client.client import OAIClient

from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.folio.runtime import FOLIO_CONFIG
from adapters.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.oai_pmh.steps.loader import LoaderRuntime
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_generator import WindowGenerator
from adapters.utils.window_store import WindowStatusRecord, WindowStore

if TYPE_CHECKING:
    from datetime import datetime

    from pyiceberg.table import Table as IcebergTable


# ---------------------------------------------------------------------------
# Stub OAI Client for tests
# ---------------------------------------------------------------------------
class StubOAIClient(OAIClient):
    """Stub OAI client that doesn't require a real endpoint."""

    def __init__(self) -> None:
        pass


# ---------------------------------------------------------------------------
# Parameterized fixtures for testing with multiple adapter configs
# ---------------------------------------------------------------------------
@pytest.fixture(params=[AXIELL_CONFIG, FOLIO_CONFIG], ids=["axiell", "folio"])
def adapter_runtime_config(request: pytest.FixtureRequest) -> OAIPMHRuntimeConfig:
    """Parameterized runtime config for testing both adapters.

    This fixture runs each test twice - once with Axiell config and once
    with FOLIO config - to ensure shared OAI-PMH logic works consistently.
    """
    config: OAIPMHRuntimeConfig = request.param
    return config


@pytest.fixture
def adapter_namespace(adapter_runtime_config: OAIPMHRuntimeConfig) -> str:
    """Get the adapter namespace from the current config."""
    return adapter_runtime_config.config.adapter_namespace


@pytest.fixture
def adapter_name(adapter_runtime_config: OAIPMHRuntimeConfig) -> str:
    """Get the adapter name from the current config."""
    return adapter_runtime_config.config.adapter_name


@pytest.fixture
def oai_metadata_prefix(adapter_runtime_config: OAIPMHRuntimeConfig) -> str:
    """Get the OAI metadata prefix from the current config."""
    return adapter_runtime_config.config.oai_metadata_prefix


@pytest.fixture
def oai_set_spec(adapter_runtime_config: OAIPMHRuntimeConfig) -> str | None:
    """Get the OAI set spec from the current config."""
    return adapter_runtime_config.config.oai_set_spec


# ---------------------------------------------------------------------------
# Helper fixtures for common test setup
# ---------------------------------------------------------------------------
@pytest.fixture
def adapter_store_client(
    temporary_table: IcebergTable,
    adapter_namespace: str,
) -> AdapterStore:
    """Create an AdapterStore with the current adapter's namespace."""
    return AdapterStore(temporary_table, default_namespace=adapter_namespace)


@pytest.fixture
def window_store(
    temporary_window_status_table: IcebergTable,
) -> WindowStore:
    """Create a WindowStore for the test."""
    return WindowStore(temporary_window_status_table)


@pytest.fixture
def loader_runtime(
    window_store: WindowStore,
    adapter_store_client: AdapterStore,
    adapter_namespace: str,
    adapter_name: str,
) -> LoaderRuntime:
    """Create a LoaderRuntime with test components."""
    window_generator = WindowGenerator()

    return LoaderRuntime(
        store=window_store,
        table_client=adapter_store_client,
        oai_client=StubOAIClient(),
        window_generator=window_generator,
        adapter_namespace=adapter_namespace,
        adapter_name=adapter_name,
    )


# ---------------------------------------------------------------------------
# Helper functions for test data creation
# ---------------------------------------------------------------------------
def create_window_row(
    start: datetime,
    end: datetime,
    state: str = "success",
    record_ids: tuple[str, ...] = (),
    tags: dict[str, str] | None = None,
) -> WindowStatusRecord:
    """Create a WindowStatusRecord for testing."""
    return WindowStatusRecord(
        window_key=f"{start.isoformat()}_{end.isoformat()}",
        window_start=start,
        window_end=end,
        state=state,
        attempts=1,
        last_error=None,
        record_ids=record_ids,
        updated_at=end,
        tags=tags,
    )


def populate_window_store(
    table: IcebergTable,
    rows: list[WindowStatusRecord],
) -> WindowStore:
    """Populate a WindowStore with test data."""
    store = WindowStore(table)
    for row in rows:
        store.upsert(row)
    return store
