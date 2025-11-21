"""Backwards-compatible shim to the shared Iceberg utilities."""

from adapters.ebsco.table_config import get_iceberg_table
from adapters.utils.iceberg import IcebergTableClient

__all__ = ["IcebergTableClient", "get_iceberg_table"]
