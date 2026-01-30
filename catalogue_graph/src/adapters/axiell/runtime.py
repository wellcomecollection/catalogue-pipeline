"""Axiell adapter runtime configuration.

Extends OAIPMHRuntimeConfig base class for the Axiell OAI-PMH adapter.
"""

from __future__ import annotations

import httpx
from oai_pmh_client.client import OAIClient

from adapters.axiell.clients import build_http_client as _build_axiell_http_client
from adapters.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig


class AxiellRuntimeConfig(OAIPMHRuntimeConfig):
    """Axiell adapter runtime configuration.

    Extends OAIPMHRuntimeConfig with Axiell-specific authentication
    and OAI client configuration.
    """

    def __init__(self, config: OAIPMHAdapterConfig | None = None):
        super().__init__(config or AXIELL_ADAPTER_CONFIG)

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL from SSM."""
        from adapters.axiell.clients import _oai_endpoint

        return _oai_endpoint()

    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for Axiell OAI-PMH requests.

        Uses the Axiell-specific Token header authentication.
        """
        return _build_axiell_http_client()

    def build_oai_client(self, *, http_client: httpx.Client | None = None) -> OAIClient:
        """Build the OAI-PMH client for harvesting records.

        Overrides base to include Axiell-specific retry configuration.
        """
        from adapters.axiell.clients import build_oai_client as _build_axiell_oai_client

        return _build_axiell_oai_client(http_client=http_client)


# Singleton instance for use by lambda handlers and CLI
AXIELL_CONFIG = AxiellRuntimeConfig()
