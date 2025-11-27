"""Shared table configuration helpers for the Axiell adapter."""

from __future__ import annotations

from pyiceberg.table import Table as IcebergTable

from adapters.axiell import config
from adapters.utils.iceberg import (
    IcebergTableClient,
    get_local_table,
    get_rest_api_table,
)


def get_iceberg_table(*, use_rest_api_table: bool = True) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            create_if_not_exists=False,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
        )

    return get_local_table(
        table_name=config.LOCAL_TABLE_NAME,
        namespace=config.LOCAL_NAMESPACE,
        db_name=config.LOCAL_DB_NAME,
    )


__all__ = ["IcebergTableClient", "get_iceberg_table"]
