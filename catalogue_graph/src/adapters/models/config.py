"""Shared adapter configuration models.

This module contains base configuration classes that are used across
all adapter types (OAI-PMH, EBSCO, etc.).
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict

from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
)


class AdapterConfig(BaseModel):
    """Base configuration for all adapters.

    This frozen Pydantic model contains common configuration fields shared
    across all adapter types (Axiell, FOLIO, EBSCO, etc.). Adapter-specific
    configuration is handled by subclasses.
    """

    model_config = ConfigDict(frozen=True)

    # ---------------------------------------------------------------------------
    # Adapter identity
    # ---------------------------------------------------------------------------
    adapter_name: str
    """Short identifier for this adapter (e.g., 'axiell', 'folio', 'ebsco')."""

    adapter_namespace: str
    """Namespace used for records in the adapter store."""

    pipeline_step_prefix: str
    """Prefix for pipeline step names (e.g., 'axiell_adapter')."""

    # ---------------------------------------------------------------------------
    # Iceberg tables (common to all adapters)
    # ---------------------------------------------------------------------------
    rest_api_iceberg_config: RestApiIcebergTableConfig
    """Configuration for remote Iceberg table via REST API."""

    local_iceberg_config: LocalIcebergTableConfig
    """Configuration for local Iceberg table."""
