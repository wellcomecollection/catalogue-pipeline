from __future__ import annotations

from unittest.mock import patch

import httpx

from adapters.folio.config import FOLIO_ADAPTER_CONFIG
from adapters.folio.runtime import FOLIO_CONFIG, FolioRuntimeConfig
from adapters.oai_pmh.http_client import OAIPMHHTTPClient
from adapters.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig


def test_folio_runtime_config_extends_base() -> None:
    assert isinstance(FOLIO_CONFIG, OAIPMHRuntimeConfig)
    assert isinstance(FOLIO_CONFIG, FolioRuntimeConfig)


def test_folio_runtime_config_has_correct_adapter_name() -> None:
    assert FOLIO_CONFIG.config.adapter_name == "folio"
    assert FOLIO_CONFIG.config.adapter_namespace == "folio"


def test_folio_runtime_config_has_correct_metadata_prefix() -> None:
    assert FOLIO_CONFIG.config.oai_metadata_prefix == "marc21_withholdings"


def test_folio_runtime_config_has_no_set_spec_by_default() -> None:
    assert FOLIO_CONFIG.config.oai_set_spec is None


def test_folio_adapter_config_is_frozen() -> None:
    assert FOLIO_ADAPTER_CONFIG.model_config.get("frozen") is True


def test_folio_runtime_build_http_client() -> None:
    with patch("adapters.folio.clients._oai_token", return_value="test-token"):
        client = FOLIO_CONFIG.build_http_client()

    assert isinstance(client, httpx.Client)
    assert isinstance(client, OAIPMHHTTPClient)
    client.close()


def test_folio_runtime_get_oai_endpoint() -> None:
    expected_url = "https://edge-wellcome.folio.ebsco.com/oai"

    with patch("adapters.folio.clients._oai_endpoint", return_value=expected_url):
        url = FOLIO_CONFIG.get_oai_endpoint()

    assert url == expected_url


def test_folio_runtime_accepts_custom_config() -> None:
    custom_config = OAIPMHAdapterConfig(
        adapter_name="folio-custom",
        adapter_namespace="folio_custom",
        pipeline_step_prefix="folio_custom_adapter",
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

    runtime = FolioRuntimeConfig(custom_config)

    assert runtime.config.adapter_name == "folio-custom"
    assert runtime.config.window_minutes == 30
    assert runtime.config.oai_set_spec == "test-set"


def test_folio_config_singleton_is_consistent() -> None:
    # Access multiple times, should be same instance
    from adapters.folio.runtime import FOLIO_CONFIG as config_import_1
    from adapters.folio.runtime import FOLIO_CONFIG as config_import_2

    assert config_import_1 is config_import_2
    assert config_import_1.config is config_import_2.config


def test_folio_runtime_build_oai_client() -> None:
    from oai_pmh_client.client import OAIClient

    with (
        patch("adapters.folio.clients._oai_token", return_value="test-token"),
        patch(
            "adapters.folio.clients._oai_endpoint",
            return_value="https://test.folio.com/oai",
        ),
    ):
        oai_client = FOLIO_CONFIG.build_oai_client()

    assert isinstance(oai_client, OAIClient)
    oai_client._client.close()
