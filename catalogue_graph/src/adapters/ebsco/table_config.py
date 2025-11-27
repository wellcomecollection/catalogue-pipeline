"""
Shared table configuration for the EBSCO adapter.
"""

from pyiceberg.table import Table as IcebergTable

from adapters.ebsco import config
from adapters.utils.iceberg import get_local_table, get_rest_api_table


def get_iceberg_table(use_rest_api_table: bool = True) -> IcebergTable:
    """Return either the REST API table or a local catalog table based on configuration."""

    if use_rest_api_table:
        print("Using S3 Tables Iceberg REST API table...")
        return get_rest_api_table(
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
        )

    print("Using local table...")
    return get_local_table(
        table_name=config.LOCAL_TABLE_NAME,
        namespace=config.LOCAL_NAMESPACE,
        db_name=config.LOCAL_DB_NAME,
    )
