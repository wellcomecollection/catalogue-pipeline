"""Runtime configuration base class for OAI-PMH adapters.

Each adapter (Axiell, FOLIO, etc.) must extend this ABC to provide
adapter-specific configuration while reusing the generic step implementations.

Auth Notes:
- Authentication is handled via the build_http_client() method
- Axiell: Custom "Token" header auth
- FOLIO: OAuth2 "Authorization: Bearer" header
- Each adapter implements its own auth strategy in build_http_client()
"""

from __future__ import annotations

from abc import ABC, abstractmethod

import httpx
from oai_pmh_client.client import OAIClient
from pydantic import BaseModel, ConfigDict

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import (
    IcebergTable,
    IcebergTableConfig,
    get_iceberg_table,
)
from adapters.utils.window_store import (
    WINDOW_STATUS_SCHEMA,
    WindowStore,
)


class OAIPMHAdapterConfig(BaseModel):
    """Configuration values for an OAI-PMH adapter.

    This is a frozen Pydantic model containing all static configuration
    values for an adapter. Runtime behavior (auth, SSM lookups) is handled
    by the OAIPMHRuntimeConfig class.
    """

    model_config = ConfigDict(frozen=True)

    # ---------------------------------------------------------------------------
    # Adapter identity
    # ---------------------------------------------------------------------------
    adapter_name: str
    """Short identifier for this adapter (e.g., 'axiell', 'folio')."""

    adapter_namespace: str
    """Namespace used for records in the adapter store."""

    pipeline_step_prefix: str
    """Prefix for pipeline step names (e.g., 'axiell_adapter')."""

    # ---------------------------------------------------------------------------
    # Window harvesting configuration
    # ---------------------------------------------------------------------------
    window_minutes: int
    """Duration of each harvesting window in minutes."""

    window_lookback_days: int
    """Days to look back when no successful windows exist."""

    max_lag_minutes: int
    """Maximum allowed lag before circuit breaker trips."""

    max_pending_windows: int | None
    """Maximum windows to process in a single batch (None = unlimited)."""

    # ---------------------------------------------------------------------------
    # OAI-PMH endpoint configuration
    # ---------------------------------------------------------------------------
    oai_metadata_prefix: str
    """OAI-PMH metadata prefix (e.g., 'oai_marcxml')."""

    oai_set_spec: str | None
    """OAI-PMH set specification (None for all records)."""

    # ---------------------------------------------------------------------------
    # Notifications
    # ---------------------------------------------------------------------------
    chatbot_topic_arn: str | None
    """SNS topic ARN for chatbot notifications (None to disable)."""

    # ---------------------------------------------------------------------------
    # Table configuration - REST API (S3 Tables)
    # ---------------------------------------------------------------------------
    s3_tables_bucket: str
    """S3 bucket for S3 Tables storage."""

    rest_api_table_name: str
    """Table name for adapter records (REST API/S3 Tables)."""

    rest_api_namespace: str
    """Namespace for adapter records (REST API/S3 Tables)."""

    window_status_table: str
    """Table name for window status (REST API/S3 Tables)."""

    window_status_namespace: str
    """Namespace for window status (REST API/S3 Tables)."""

    aws_region: str | None = None
    """AWS region (None for auto-detect)."""

    aws_account_id: str | None = None
    """AWS account ID (None for auto-detect)."""

    # ---------------------------------------------------------------------------
    # Table configuration - Local (SQLite)
    # ---------------------------------------------------------------------------
    local_db_name: str
    """SQLite database name for local development."""

    local_table_name: str
    """Table name for adapter records (local)."""

    local_namespace: str
    """Namespace for adapter records (local)."""

    local_window_status_db_name: str
    """SQLite database name for window status (local)."""

    local_window_status_table: str
    """Table name for window status (local)."""

    local_window_status_namespace: str
    """Namespace for window status (local)."""

    # ---------------------------------------------------------------------------
    # Reporting
    # ---------------------------------------------------------------------------
    report_s3_bucket: str | None = None
    """S3 bucket for report storage (None to disable S3 report publishing)."""

    report_s3_prefix: str = "dev"
    """S3 key prefix for report paths."""


class OAIPMHRuntimeConfig(ABC):
    """Base class for OAI-PMH adapter runtime configuration.

    Adapters must extend this class and implement:
    - build_http_client(): Returns an authenticated httpx.Client
    - get_oai_endpoint(): Returns the OAI-PMH endpoint URL (may involve SSM lookup)

    The base class provides concrete implementations of factory methods
    for building tables and stores using the config values.
    """

    def __init__(self, config: OAIPMHAdapterConfig):
        self._config = config

    @property
    def config(self) -> OAIPMHAdapterConfig:
        """The adapter configuration."""
        return self._config

    # ---------------------------------------------------------------------------
    # Abstract methods (adapter-specific behavior)
    # ---------------------------------------------------------------------------
    @abstractmethod
    def build_http_client(self) -> httpx.Client:
        """Build an authenticated HTTP client for OAI-PMH requests.

        This method should return an httpx.Client configured with
        the appropriate authentication for the OAI-PMH endpoint.

        Auth strategies:
        - Axiell: Custom "Token" header
        - FOLIO: OAuth2 "Authorization: Bearer <token>" header
        """
        ...

    @abstractmethod
    def get_oai_endpoint(self) -> str:
        """Get the OAI-PMH endpoint URL.

        This may involve runtime lookups (e.g., SSM Parameter Store).
        """
        ...

    # ---------------------------------------------------------------------------
    # Factory methods (concrete implementations)
    # ---------------------------------------------------------------------------
    def build_adapter_table(
        self,
        *,
        use_rest_api_table: bool = True,
        create_if_not_exists: bool = True,
    ) -> IcebergTable:
        """Build the Iceberg table for storing harvested records."""
        cfg = self._config
        if use_rest_api_table:
            table_config = IcebergTableConfig(
                table_name=cfg.rest_api_table_name,
                namespace=cfg.rest_api_namespace,
                use_rest_api_table=True,
                create_if_not_exists=create_if_not_exists,
                s3_tables_bucket=cfg.s3_tables_bucket,
                region=cfg.aws_region,
                account_id=cfg.aws_account_id,
                db_name=cfg.local_db_name,
            )
        else:
            table_config = IcebergTableConfig(
                table_name=cfg.local_table_name,
                namespace=cfg.local_namespace,
                use_rest_api_table=False,
                create_if_not_exists=create_if_not_exists,
                db_name=cfg.local_db_name,
            )

        return get_iceberg_table(table_config)

    def _build_window_status_table(
        self,
        *,
        use_rest_api_table: bool = True,
        create_if_not_exists: bool = True,
    ) -> IcebergTable:
        """Build the Iceberg table for tracking window status."""
        cfg = self._config
        if use_rest_api_table:
            table_config = IcebergTableConfig(
                table_name=cfg.window_status_table,
                namespace=cfg.window_status_namespace,
                use_rest_api_table=True,
                create_if_not_exists=create_if_not_exists,
                s3_tables_bucket=cfg.s3_tables_bucket,
                region=cfg.aws_region,
                account_id=cfg.aws_account_id,
            )
        else:
            table_config = IcebergTableConfig(
                table_name=cfg.local_window_status_table,
                namespace=cfg.local_window_status_namespace,
                use_rest_api_table=False,
                create_if_not_exists=create_if_not_exists,
                db_name=cfg.local_window_status_db_name,
            )

        return get_iceberg_table(
            table_config,
            schema=WINDOW_STATUS_SCHEMA,
            partition_spec=None,
        )

    def build_window_store(self, *, use_rest_api_table: bool = True) -> WindowStore:
        """Build the window status store for tracking harvest progress."""
        table = self._build_window_status_table(use_rest_api_table=use_rest_api_table)
        return WindowStore(table)

    def build_adapter_store(self, *, use_rest_api_table: bool = True) -> AdapterStore:
        """Build the adapter store wrapping the Iceberg table."""
        table = self.build_adapter_table(use_rest_api_table=use_rest_api_table)
        return AdapterStore(table, default_namespace=self.config.adapter_namespace)

    def build_oai_client(self, *, http_client: httpx.Client | None = None) -> OAIClient:
        """Build the OAI-PMH client for harvesting records."""
        client = http_client or self.build_http_client()
        return OAIClient(
            self.get_oai_endpoint(),
            client=client,
        )
