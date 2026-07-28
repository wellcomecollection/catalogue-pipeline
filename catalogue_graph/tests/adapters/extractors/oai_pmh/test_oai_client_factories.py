"""Factory wiring tests for the OAI-PMH client builders.

Transient-failure retry behaviour (empty bodies, transport errors, 5xx, and
resumption-token handling) lives upstream in oai-pmh-client and is tested
there; these tests only assert that each build_oai_client factory configures
the upstream client from the adapter's constants.
"""

from __future__ import annotations

from unittest.mock import patch

import httpx
from oai_pmh_client.client import OAIClient

BASE_URL = "http://oai.test/oai"


def test_base_runtime_build_oai_client_uses_upstream_defaults() -> None:
    from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
    from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig

    class StubRuntimeConfig(OAIPMHRuntimeConfig):
        def build_http_client(self) -> httpx.Client:
            return httpx.Client(transport=httpx.MockTransport(lambda _: None))  # type: ignore[arg-type]

        def get_oai_endpoint(self) -> str:
            return BASE_URL

    oai_client = StubRuntimeConfig(AXIELL_ADAPTER_CONFIG).build_oai_client()

    assert isinstance(oai_client, OAIClient)
    assert oai_client.base_url == BASE_URL
    assert oai_client.max_transient_retries == 3
    oai_client._client.close()


def test_axiell_build_oai_client_is_configured_from_constants() -> None:
    from adapters.extractors.oai_pmh.axiell import clients, config

    with (
        patch.object(clients, "_oai_token", return_value="test-token"),
        patch.object(clients, "_oai_endpoint", return_value=BASE_URL),
    ):
        oai_client = clients.build_oai_client()

    assert isinstance(oai_client, OAIClient)
    assert oai_client.base_url == BASE_URL
    assert oai_client.max_transient_retries == config.OAI_TRANSIENT_RETRIES
    assert oai_client.max_request_retries == max(1, config.OAI_MAX_RETRIES)
    assert oai_client.request_backoff_factor == config.OAI_BACKOFF_FACTOR
    assert oai_client.request_max_backoff == config.OAI_BACKOFF_MAX
    oai_client._client.close()


def test_axiell_runtime_delegates_to_clients_factory() -> None:
    from adapters.extractors.oai_pmh.axiell import clients, config
    from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG

    with (
        patch.object(clients, "_oai_token", return_value="test-token"),
        patch.object(clients, "_oai_endpoint", return_value=BASE_URL),
    ):
        oai_client = AXIELL_CONFIG.build_oai_client()

    assert isinstance(oai_client, OAIClient)
    assert oai_client.max_transient_retries == config.OAI_TRANSIENT_RETRIES
    oai_client._client.close()


def test_axiell_build_oai_client_honours_request_retry_override() -> None:
    from adapters.extractors.oai_pmh.axiell import clients, config
    from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG

    override = config.OAI_MAX_RETRIES + 4

    with (
        patch.object(clients, "_oai_token", return_value="test-token"),
        patch.object(clients, "_oai_endpoint", return_value=BASE_URL),
    ):
        oai_client = AXIELL_CONFIG.build_oai_client(max_request_retries=override)

    assert oai_client.max_request_retries == override
    # The override must not disturb the other retry settings.
    assert oai_client.max_transient_retries == config.OAI_TRANSIENT_RETRIES
    assert oai_client.request_backoff_factor == config.OAI_BACKOFF_FACTOR
    oai_client._client.close()


def test_folio_build_oai_client_honours_request_retry_override() -> None:
    from adapters.extractors.oai_pmh.folio import runtime as folio_runtime
    from adapters.extractors.oai_pmh.folio.config import (
        OAI_MAX_RETRIES,
        OAI_TRANSIENT_RETRIES,
    )

    override = OAI_MAX_RETRIES + 4

    with patch.object(folio_runtime, "_oai_endpoint", return_value=BASE_URL):
        config_obj = folio_runtime.FOLIO_CONFIG
        with patch.object(config_obj, "build_http_client", return_value=httpx.Client()):
            default_client = config_obj.build_oai_client()
            overridden = config_obj.build_oai_client(max_request_retries=override)

    assert default_client.max_request_retries == max(1, OAI_MAX_RETRIES)
    assert overridden.max_request_retries == override
    assert overridden.max_transient_retries == OAI_TRANSIENT_RETRIES
    default_client._client.close()
    overridden._client.close()


def test_base_runtime_build_oai_client_honours_request_retry_override() -> None:
    from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
    from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig

    class StubRuntimeConfig(OAIPMHRuntimeConfig):
        def build_http_client(self) -> httpx.Client:
            return httpx.Client(transport=httpx.MockTransport(lambda _: None))  # type: ignore[arg-type]

        def get_oai_endpoint(self) -> str:
            return BASE_URL

    oai_client = StubRuntimeConfig(AXIELL_ADAPTER_CONFIG).build_oai_client(
        max_request_retries=7
    )

    assert oai_client.max_request_retries == 7
    oai_client._client.close()


def test_rebuild_download_asks_for_a_larger_retry_budget() -> None:
    """The rebuild download cannot resume, so it must not inherit the small
    windowed-harvest retry budget."""
    from adapters.extractors.oai_pmh.axiell import config as axiell_config
    from scripts.rebuild_adapter import DOWNLOAD_MAX_REQUEST_RETRIES

    assert DOWNLOAD_MAX_REQUEST_RETRIES > axiell_config.OAI_MAX_RETRIES
