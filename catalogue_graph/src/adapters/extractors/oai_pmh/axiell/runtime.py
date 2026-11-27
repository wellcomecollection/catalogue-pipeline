from __future__ import annotations

import httpx
from oai_pmh_client.client import OAIClient

from adapters.extractors.oai_pmh.axiell import config as axiell_config
from adapters.extractors.oai_pmh.axiell.clients import (
    _oai_endpoint,
)
from adapters.extractors.oai_pmh.axiell.clients import (
    build_http_client as _build_axiell_http_client,
)
from adapters.extractors.oai_pmh.axiell.clients import (
    build_oai_client as _build_axiell_oai_client,
)
from adapters.extractors.oai_pmh.axiell.config import AXIELL_ADAPTER_CONFIG
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig, OAIPMHRuntimeConfig
from adapters.utils.iceberg import (
    IcebergTable,
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
    get_local_table,
    get_rest_api_table,
)


class AxiellRuntimeConfig(OAIPMHRuntimeConfig):
    """Axiell adapter runtime configuration.

    Extends OAIPMHRuntimeConfig with Axiell-specific authentication
    and OAI client configuration.

    Everything an instance connects to (the OAI-PMH endpoint and token, an
    optional NTLM login, and the reconciler and deletion facts tables) comes
    from its constructor, so that the live system and UAT can each have one.
    The defaults are the live system's, which has no NTLM login.
    """

    def __init__(
        self,
        config: OAIPMHAdapterConfig | None = None,
        *,
        ssm_oai_url: str | None = None,
        ssm_oai_token: str | None = None,
        ssm_oai_ntlm_username: str | None = None,
        ssm_oai_ntlm_password: str | None = None,
        reconciler_rest_api_config: RestApiIcebergTableConfig | None = None,
        reconciler_local_config: LocalIcebergTableConfig | None = None,
        deletion_facts_rest_api_config: RestApiIcebergTableConfig | None = None,
        deletion_facts_local_config: LocalIcebergTableConfig | None = None,
    ):
        super().__init__(config or AXIELL_ADAPTER_CONFIG)
        self.ssm_oai_url = ssm_oai_url or axiell_config.SSM_OAI_URL
        self.ssm_oai_token = ssm_oai_token or axiell_config.SSM_OAI_TOKEN
        if (ssm_oai_ntlm_username is None) != (ssm_oai_ntlm_password is None):
            raise ValueError("An NTLM login needs both a username and a password")
        self.ssm_oai_ntlm_username = ssm_oai_ntlm_username
        self.ssm_oai_ntlm_password = ssm_oai_ntlm_password
        self.reconciler_rest_api_config = (
            reconciler_rest_api_config or axiell_config.RECONCILER_REST_API_CONFIG
        )
        self.reconciler_local_config = (
            reconciler_local_config or axiell_config.RECONCILER_LOCAL_CONFIG
        )
        self.deletion_facts_rest_api_config = (
            deletion_facts_rest_api_config
            or axiell_config.DELETION_FACTS_REST_API_CONFIG
        )
        self.deletion_facts_local_config = (
            deletion_facts_local_config or axiell_config.DELETION_FACTS_LOCAL_CONFIG
        )

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL from SSM."""
        return _oai_endpoint(self.ssm_oai_url)

    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for Axiell OAI-PMH requests.

        Uses the Axiell-specific Token header authentication, plus an NTLM
        login when this instance has one.
        """
        return _build_axiell_http_client(
            ssm_token_param=self.ssm_oai_token,
            ssm_ntlm_username_param=self.ssm_oai_ntlm_username,
            ssm_ntlm_password_param=self.ssm_oai_ntlm_password,
        )

    def build_oai_client(
        self,
        *,
        http_client: httpx.Client | None = None,
        max_request_retries: int | None = None,
    ) -> OAIClient:
        """Build the OAI-PMH client for harvesting records.

        Overrides base to include Axiell-specific retry configuration.
        """
        return _build_axiell_oai_client(
            http_client=http_client,
            max_request_retries=max_request_retries,
            ssm_url_param=self.ssm_oai_url,
            ssm_token_param=self.ssm_oai_token,
            ssm_ntlm_username_param=self.ssm_oai_ntlm_username,
            ssm_ntlm_password_param=self.ssm_oai_ntlm_password,
        )

    def build_reconciler_table(
        self,
        *,
        use_rest_api_table: bool = True,
        create_if_not_exists: bool = True,
    ) -> IcebergTable:
        """Build the Iceberg table for the Axiell reconciler store."""
        if use_rest_api_table:
            return get_rest_api_table(
                self.reconciler_rest_api_config, create_if_not_exists
            )
        return get_local_table(self.reconciler_local_config, create_if_not_exists)

    def build_deletion_facts_table(
        self,
        *,
        use_rest_api_table: bool = True,
        create_if_not_exists: bool = True,
    ) -> IcebergTable:
        """Build the Iceberg table for the Axiell deletion facts store."""
        if use_rest_api_table:
            return get_rest_api_table(
                self.deletion_facts_rest_api_config, create_if_not_exists
            )
        return get_local_table(self.deletion_facts_local_config, create_if_not_exists)


# Singleton instances for use by lambda handlers and CLI
AXIELL_CONFIG = AxiellRuntimeConfig()

AXIELL_UAT_CONFIG = AxiellRuntimeConfig(
    axiell_config.AXIELL_UAT_ADAPTER_CONFIG,
    ssm_oai_url=axiell_config.UAT_SSM_OAI_URL,
    ssm_oai_token=axiell_config.UAT_SSM_OAI_TOKEN,
    ssm_oai_ntlm_username=axiell_config.UAT_SSM_OAI_NTLM_USERNAME,
    ssm_oai_ntlm_password=axiell_config.UAT_SSM_OAI_NTLM_PASSWORD,
    reconciler_rest_api_config=axiell_config.UAT_RECONCILER_REST_API_CONFIG,
    reconciler_local_config=axiell_config.UAT_RECONCILER_LOCAL_CONFIG,
    deletion_facts_rest_api_config=axiell_config.UAT_DELETION_FACTS_REST_API_CONFIG,
    deletion_facts_local_config=axiell_config.UAT_DELETION_FACTS_LOCAL_CONFIG,
)
