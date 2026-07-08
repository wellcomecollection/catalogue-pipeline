from __future__ import annotations

from collections.abc import Iterator

import httpx
from oai_pmh_client.client import OAIClient

from adapters.extractors.oai_pmh.axiell.clients import (
    _oai_endpoint,
)
from adapters.extractors.oai_pmh.axiell.clients import (
    build_http_client as _build_axiell_http_client,
)
from adapters.extractors.oai_pmh.axiell.clients import (
    build_oai_client as _build_axiell_oai_client,
)
from adapters.extractors.oai_pmh.axiell.clients import (
    build_webapi_client as _build_axiell_webapi_client,
)
from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig
from adapters.utils.iceberg import IcebergTable, get_local_table, get_rest_api_table


class AxiellRuntimeConfig(OAIPMHRuntimeConfig):
    """Axiell adapter runtime configuration.

    Extends OAIPMHRuntimeConfig with Axiell-specific authentication
    and OAI client configuration.
    """

    def __init__(self, config: OAIPMHAdapterConfig | None = None):
        super().__init__(config or AXIELL_ADAPTER_CONFIG)

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL from SSM."""
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
        return _build_axiell_oai_client(http_client=http_client)

    def _webapi_database(self) -> str:
        return self.config.oai_set_spec or "collect"

    def source_of_truth_count(self) -> int | None:
        """Total record count from the Axiell WebAPI (wwwopac.ashx).

        A direct database count, so unaffected by the OAI module's
        datestamp-query faults.
        """
        return _build_axiell_webapi_client(database=self._webapi_database()).count()

    def enumerate_source_ids(self) -> Iterator[str]:
        """Yield every record id via the Axiell WebAPI (stateless paging).

        Ids are ``<database>:<priref>`` to match the OAI identifiers stored in
        the adapter table.
        """
        database = self._webapi_database()
        return _build_axiell_webapi_client(database=database).enumerate_ids(
            namespace=database
        )

    def build_reconciler_table(
        self,
        *,
        use_rest_api_table: bool = True,
        create_if_not_exists: bool = True,
    ) -> IcebergTable:
        """Build the Iceberg table for the Axiell reconciler store."""
        from adapters.extractors.oai_pmh.axiell.config import (
            RECONCILER_LOCAL_CONFIG,
            RECONCILER_REST_API_CONFIG,
        )

        if use_rest_api_table:
            return get_rest_api_table(RECONCILER_REST_API_CONFIG, create_if_not_exists)
        return get_local_table(RECONCILER_LOCAL_CONFIG, create_if_not_exists)


# Singleton instance for use by lambda handlers and CLI
AXIELL_CONFIG = AxiellRuntimeConfig()
