"""How `build_window_store` decides whether to create the table it opens."""

from __future__ import annotations

from typing import Any

import pytest

from adapters.extractors.oai_pmh import runtime as runtime_module
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig


@pytest.fixture
def created(monkeypatch: pytest.MonkeyPatch) -> list[bool]:
    """Record the create flag each table getter is called with."""
    calls: list[bool] = []

    def record(_config: Any, create_if_not_exists: bool) -> object:
        calls.append(create_if_not_exists)
        return object()

    monkeypatch.setattr(runtime_module, "get_rest_api_table", record)
    monkeypatch.setattr(runtime_module, "get_local_table", record)
    return calls


@pytest.mark.parametrize("use_rest_api_table", [True, False])
def test_opting_out_reaches_the_table_getter(
    adapter_runtime_config: OAIPMHRuntimeConfig,
    created: list[bool],
    use_rest_api_table: bool,
) -> None:
    """A read-only caller must not trigger s3tables:CreateNamespace."""
    adapter_runtime_config.build_window_store(
        use_rest_api_table=use_rest_api_table, create_if_not_exists=False
    )

    assert created == [False]


@pytest.mark.parametrize("use_rest_api_table", [True, False])
def test_the_default_still_creates(
    adapter_runtime_config: OAIPMHRuntimeConfig,
    created: list[bool],
    use_rest_api_table: bool,
) -> None:
    adapter_runtime_config.build_window_store(use_rest_api_table=use_rest_api_table)

    assert created == [True]
