"""FOLIO adapter runtime configuration.

Extends OAIPMHRuntimeConfig base class for the FOLIO OAI-PMH adapter.
"""

from __future__ import annotations

import httpx
from oai_pmh_client.client import OAIClient

from adapters.folio import config
from adapters.folio.clients import build_http_client as _build_folio_http_client
from adapters.folio.config import FOLIO_ADAPTER_CONFIG
from adapters.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig


class FolioRuntimeConfig(OAIPMHRuntimeConfig):
    """FOLIO adapter runtime configuration.

    Extends OAIPMHRuntimeConfig with FOLIO-specific authentication
    and OAI client configuration.
    """

    def __init__(self, cfg: OAIPMHAdapterConfig | None = None):
        super().__init__(cfg or FOLIO_ADAPTER_CONFIG)

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL from SSM."""
        from adapters.folio.clients import _oai_endpoint

        return _oai_endpoint()

    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for FOLIO OAI-PMH requests.

        Uses the FOLIO Authorization header authentication.
        """
        return _build_folio_http_client()

    def build_oai_client(self, *, http_client: httpx.Client | None = None) -> OAIClient:
        """Build the OAI-PMH client for harvesting records.

        Overrides base to include FOLIO-specific retry configuration.
        """
        client = http_client or self.build_http_client()
        return OAIClient(
            self.get_oai_endpoint(),
            client=client,
            max_request_retries=config.OAI_MAX_RETRIES,
            request_backoff_factor=config.OAI_BACKOFF_FACTOR,
            request_max_backoff=config.OAI_BACKOFF_MAX,
        )


# Singleton instance for use by lambda handlers and CLI
FOLIO_CONFIG = FolioRuntimeConfig()
