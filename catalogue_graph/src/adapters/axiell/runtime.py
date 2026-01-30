"""Axiell adapter runtime configuration.

Implements OAIPMHRuntimeConfig protocol for the Axiell OAI-PMH adapter.
"""

from __future__ import annotations

import httpx
from oai_pmh_client.client import OAIClient

from adapters.axiell import config, helpers
from adapters.axiell.clients import build_http_client as _build_axiell_http_client
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import IcebergTable
from adapters.utils.window_store import WindowStore


class AxiellRuntimeConfig:
    """Axiell adapter configuration implementing OAIPMHRuntimeConfig.

    This class provides Axiell-specific configuration values and factory methods
    for building clients and stores as required by the generic OAI-PMH steps.
    """

    # ---------------------------------------------------------------------------
    # Adapter identity
    # ---------------------------------------------------------------------------
    @property
    def adapter_name(self) -> str:
        return "axiell"

    @property
    def adapter_namespace(self) -> str:
        return "axiell"

    @property
    def pipeline_step_prefix(self) -> str:
        return "axiell_adapter"

    # ---------------------------------------------------------------------------
    # Window harvesting configuration
    # ---------------------------------------------------------------------------
    @property
    def window_minutes(self) -> int:
        return config.WINDOW_MINUTES

    @property
    def window_lookback_days(self) -> int:
        return config.WINDOW_LOOKBACK_DAYS

    @property
    def max_lag_minutes(self) -> int:
        return config.MAX_LAG_MINUTES

    @property
    def max_pending_windows(self) -> int | None:
        return config.MAX_PENDING_WINDOWS

    # ---------------------------------------------------------------------------
    # OAI-PMH endpoint configuration
    # ---------------------------------------------------------------------------
    @property
    def oai_metadata_prefix(self) -> str:
        return config.OAI_METADATA_PREFIX

    @property
    def oai_set_spec(self) -> str | None:
        return config.OAI_SET_SPEC

    # ---------------------------------------------------------------------------
    # Notifications
    # ---------------------------------------------------------------------------
    @property
    def chatbot_topic_arn(self) -> str | None:
        return config.CHATBOT_TOPIC_ARN

    # ---------------------------------------------------------------------------
    # Factory methods
    # ---------------------------------------------------------------------------
    def build_window_store(self, *, use_rest_api_table: bool = True) -> WindowStore:
        """Build the window status store for tracking harvest progress."""
        return helpers.build_window_store(use_rest_api_table=use_rest_api_table)

    def build_adapter_table(self, *, use_rest_api_table: bool = True) -> IcebergTable:
        """Build the Iceberg table for storing harvested records."""
        return helpers.build_adapter_table(use_rest_api_table=use_rest_api_table)

    def build_adapter_store(self, *, use_rest_api_table: bool = True) -> AdapterStore:
        """Build the adapter store wrapping the Iceberg table."""
        table = self.build_adapter_table(use_rest_api_table=use_rest_api_table)
        return AdapterStore(table, default_namespace=self.adapter_namespace)

    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for Axiell OAI-PMH requests.

        Uses the Axiell-specific Token header authentication.
        """
        return _build_axiell_http_client()

    def build_oai_client(self, *, http_client: httpx.Client | None = None) -> OAIClient:
        """Build the OAI-PMH client for harvesting records."""
        from adapters.axiell.clients import build_oai_client as _build_axiell_oai_client

        return _build_axiell_oai_client(http_client=http_client)

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL from SSM."""
        from adapters.axiell.clients import _oai_endpoint

        return _oai_endpoint()


# Singleton instance for use by lambda handlers and CLI
AXIELL_CONFIG = AxiellRuntimeConfig()
