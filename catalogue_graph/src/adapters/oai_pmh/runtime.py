"""Runtime configuration protocol for OAI-PMH adapters.

Each adapter (Axiell, FOLIO, etc.) must implement this protocol to provide
adapter-specific configuration while reusing the generic step implementations.

Auth Notes:
- Authentication is handled via the build_http_client() method
- Axiell: Custom "Token" header auth
- FOLIO: OAuth2 "Authorization: Bearer" header
- Each adapter implements its own auth strategy in build_http_client()
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol, runtime_checkable

import httpx

if TYPE_CHECKING:
    from oai_pmh_client.client import OAIClient

    from adapters.utils.adapter_store import AdapterStore
    from adapters.utils.iceberg import IcebergTable
    from adapters.utils.window_store import WindowStore


@runtime_checkable
class OAIPMHRuntimeConfig(Protocol):
    """Protocol defining the interface for OAI-PMH adapter configurations.

    Adapters must implement this protocol to provide their specific
    configuration values and factory methods for building clients and stores.
    """

    # ---------------------------------------------------------------------------
    # Adapter identity
    # ---------------------------------------------------------------------------
    @property
    def adapter_name(self) -> str:
        """Short identifier for this adapter (e.g., 'axiell', 'folio')."""
        ...

    @property
    def adapter_namespace(self) -> str:
        """Namespace used for records in the adapter store."""
        ...

    @property
    def pipeline_step_prefix(self) -> str:
        """Prefix for pipeline step names (e.g., 'axiell_adapter')."""
        ...

    # ---------------------------------------------------------------------------
    # Window harvesting configuration
    # ---------------------------------------------------------------------------
    @property
    def window_minutes(self) -> int:
        """Duration of each harvesting window in minutes."""
        ...

    @property
    def window_lookback_days(self) -> int:
        """Days to look back when no successful windows exist."""
        ...

    @property
    def max_lag_minutes(self) -> int:
        """Maximum allowed lag before circuit breaker trips."""
        ...

    @property
    def max_pending_windows(self) -> int | None:
        """Maximum windows to process in a single batch (None = unlimited)."""
        ...

    # ---------------------------------------------------------------------------
    # OAI-PMH endpoint configuration
    # ---------------------------------------------------------------------------
    @property
    def oai_metadata_prefix(self) -> str:
        """OAI-PMH metadata prefix (e.g., 'oai_marcxml')."""
        ...

    @property
    def oai_set_spec(self) -> str | None:
        """OAI-PMH set specification (None for all records)."""
        ...

    # ---------------------------------------------------------------------------
    # Notifications
    # ---------------------------------------------------------------------------
    @property
    def chatbot_topic_arn(self) -> str | None:
        """SNS topic ARN for chatbot notifications (None to disable)."""
        ...

    # ---------------------------------------------------------------------------
    # Factory methods
    # ---------------------------------------------------------------------------
    def build_window_store(self, *, use_rest_api_table: bool = True) -> WindowStore:
        """Build the window status store for tracking harvest progress."""
        ...

    def build_adapter_table(self, *, use_rest_api_table: bool = True) -> IcebergTable:
        """Build the Iceberg table for storing harvested records."""
        ...

    def build_adapter_store(self, *, use_rest_api_table: bool = True) -> AdapterStore:
        """Build the adapter store wrapping the Iceberg table."""
        ...

    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for OAI-PMH requests.

        This method should return an httpx.Client configured with
        the appropriate authentication for the OAI-PMH endpoint.

        Auth strategies:
        - Axiell: Custom "Token" header
        - FOLIO: OAuth2 "Authorization: Bearer <token>" header
        """
        ...

    def build_oai_client(self, *, http_client: httpx.Client | None = None) -> OAIClient:
        """Build the OAI-PMH client for harvesting records."""
        ...

    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL."""
        ...
