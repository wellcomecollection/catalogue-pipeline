"""The Axiell UAT adapter and its isolation from the live one.

UAT is a copy of the live system, so its records share GUIDs and keys with live
ones. Anything the two adapters share (a table, the window status namespace, an
SSM parameter) would mix test records into the live store, which the catalogue
pipeline and the FOLIO sync read. These tests pin that nothing is shared.
"""

from __future__ import annotations

from collections.abc import Iterator
from unittest.mock import patch

import pytest
from httpx_ntlm import HttpNtlmAuth

from adapters.extractors.oai_pmh.axiell import clients
from adapters.extractors.oai_pmh.axiell.runtime import (
    AXIELL_CONFIG,
    AXIELL_UAT_CONFIG,
    AxiellRuntimeConfig,
)
from adapters.extractors.oai_pmh.registry import get_config


@pytest.fixture(autouse=True)
def _fresh_ssm_caches() -> Iterator[None]:
    """The SSM lookups are cached per parameter name, so a value served by one
    test must not leak into the next."""
    for cached in (clients._oai_token, clients._oai_endpoint, clients._ntlm_auth):
        cached.cache_clear()
    yield
    for cached in (clients._oai_token, clients._oai_endpoint, clients._ntlm_auth):
        cached.cache_clear()


def test_the_registry_resolves_axiell_uat() -> None:
    assert get_config("axiell_uat") is AXIELL_UAT_CONFIG
    assert isinstance(AXIELL_UAT_CONFIG, AxiellRuntimeConfig)


def test_adapter_name_matches_the_registry_key() -> None:
    """The trigger stamps adapter_type on the loader event from adapter_name, so
    a mismatch would send UAT windows to another adapter's config."""
    cfg = AXIELL_UAT_CONFIG.config
    assert cfg.adapter_name == "axiell_uat"
    assert cfg.adapter_namespace == "axiell_uat"
    assert cfg.pipeline_step_prefix == "axiell_uat_adapter"


def test_uat_harvests_the_same_format_as_live() -> None:
    """Same MARC output, so the mapping applies unchanged."""
    assert (
        AXIELL_UAT_CONFIG.config.oai_metadata_prefix
        == AXIELL_CONFIG.config.oai_metadata_prefix
    )
    assert AXIELL_UAT_CONFIG.config.oai_set_spec == "collect"


def _rest_api_tables(runtime: AxiellRuntimeConfig) -> dict[str, tuple[str, str, str]]:
    cfg = runtime.config
    tables = {
        "adapter": cfg.rest_api_iceberg_config,
        "window status": cfg.rest_api_window_status_iceberg_config,
        "reconciler": runtime.reconciler_rest_api_config,
        "deletion facts": runtime.deletion_facts_rest_api_config,
    }
    return {
        name: (t.s3_tables_bucket, t.namespace, t.table_name)
        for name, t in tables.items()
    }


def test_no_rest_api_table_is_shared_with_live() -> None:
    live = _rest_api_tables(AXIELL_CONFIG)
    uat = _rest_api_tables(AXIELL_UAT_CONFIG)

    assert set(live.values()).isdisjoint(uat.values())
    # Each in the UAT bucket, not merely under a different name.
    assert {bucket for bucket, _, _ in uat.values()} == {
        "wellcomecollection-platform-axiell-uat-adapter"
    }
    assert uat["adapter"][2] == "axiell_uat_adapter_table"


def test_no_local_table_is_shared_with_live() -> None:
    def local(runtime: AxiellRuntimeConfig) -> set[tuple[str, str, str]]:
        cfg = runtime.config
        tables = [
            cfg.local_iceberg_config,
            cfg.local_window_status_iceberg_config,
            runtime.reconciler_local_config,
            runtime.deletion_facts_local_config,
        ]
        return {(t.db_name, t.namespace, t.table_name) for t in tables}

    assert local(AXIELL_CONFIG).isdisjoint(local(AXIELL_UAT_CONFIG))


def test_uat_reads_its_own_ssm_parameters() -> None:
    assert AXIELL_UAT_CONFIG.ssm_oai_url == "/catalogue_pipeline/axiell_uat/oai_api_url"
    assert (
        AXIELL_UAT_CONFIG.ssm_oai_token
        == "/catalogue_pipeline/axiell_uat/oai_api_token"
    )
    assert (
        AXIELL_UAT_CONFIG.ssm_oai_ntlm_username
        == "/catalogue_pipeline/axiell_uat/oai_username"
    )
    assert (
        AXIELL_UAT_CONFIG.ssm_oai_ntlm_password
        == "/catalogue_pipeline/axiell_uat/oai_password"
    )
    assert AXIELL_UAT_CONFIG.ssm_oai_url != AXIELL_CONFIG.ssm_oai_url
    assert AXIELL_UAT_CONFIG.ssm_oai_token != AXIELL_CONFIG.ssm_oai_token


def test_the_uat_oai_client_is_built_from_the_uat_parameters() -> None:
    """The live adapter's endpoint and token must never be fetched for UAT."""
    values = {
        "/catalogue_pipeline/axiell_uat/oai_api_url": "https://uat.test/oai",
        "/catalogue_pipeline/axiell_uat/oai_api_token": "uat-token",
        "/catalogue_pipeline/axiell_uat/oai_username": "DOMAIN\\uat-user",
        "/catalogue_pipeline/axiell_uat/oai_password": "uat-password",
    }
    fetched: list[str] = []

    def fake_get(name: str) -> str:
        fetched.append(name)
        return values[name]

    with patch.object(clients, "get_ssm_parameter", side_effect=fake_get):
        oai_client = AXIELL_UAT_CONFIG.build_oai_client()

    assert oai_client.base_url == "https://uat.test/oai"
    assert sorted(fetched) == sorted(values)
    oai_client._client.close()


def test_live_and_uat_endpoints_are_cached_separately() -> None:
    values = {
        AXIELL_CONFIG.ssm_oai_url: "https://live.test/oai",
        AXIELL_UAT_CONFIG.ssm_oai_url: "https://uat.test/oai",
    }

    with patch.object(clients, "get_ssm_parameter", side_effect=values.__getitem__):
        assert AXIELL_CONFIG.get_oai_endpoint() == "https://live.test/oai"
        assert AXIELL_UAT_CONFIG.get_oai_endpoint() == "https://uat.test/oai"


def test_uat_logs_in_with_ntlm_as_well_as_the_token() -> None:
    """The UAT endpoint is behind IIS Windows authentication, and refuses a
    request that carries only the token."""
    values = {
        AXIELL_UAT_CONFIG.ssm_oai_token: "uat-token",
        "/catalogue_pipeline/axiell_uat/oai_username": "DOMAIN\\uat-user",
        "/catalogue_pipeline/axiell_uat/oai_password": "uat-password",
    }

    with patch.object(clients, "get_ssm_parameter", side_effect=values.__getitem__):
        http = AXIELL_UAT_CONFIG.build_http_client()

    assert isinstance(http.auth, HttpNtlmAuth)
    assert http.auth.username == "DOMAIN\\uat-user"
    assert http.auth.password == "uat-password"
    request = http.build_request("GET", "https://uat.test/oai")
    assert request.headers["Token"] == "uat-token"
    http.close()


def test_live_sends_the_token_only() -> None:
    """No NTLM login for live, and its parameters are never fetched."""
    fetched: list[str] = []

    def fake_get(name: str) -> str:
        fetched.append(name)
        return "live-token"

    with patch.object(clients, "get_ssm_parameter", side_effect=fake_get):
        http = AXIELL_CONFIG.build_http_client()

    assert AXIELL_CONFIG.ssm_oai_ntlm_username is None
    assert not isinstance(http.auth, HttpNtlmAuth)
    assert fetched == [AXIELL_CONFIG.ssm_oai_token]
    http.close()


def test_an_ntlm_login_needs_both_parameters() -> None:
    with pytest.raises(ValueError, match="both a username and a password"):
        AxiellRuntimeConfig(ssm_oai_ntlm_username="/some/username")
