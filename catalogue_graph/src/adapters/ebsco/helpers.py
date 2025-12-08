import adapters.ebsco.config as config
from adapters.utils.iceberg import (
    IcebergTable,
    IcebergTableConfig,
    get_iceberg_table,
)


def build_adapter_table(use_rest_api_table: bool) -> IcebergTable:
    if use_rest_api_table:
        table_config = IcebergTableConfig(
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            use_rest_api_table=True,
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
            db_name=config.LOCAL_DB_NAME,
        )
    else:
        table_config = IcebergTableConfig(
            table_name=config.LOCAL_TABLE_NAME,
            namespace=config.LOCAL_NAMESPACE,
            use_rest_api_table=False,
            db_name=config.LOCAL_DB_NAME,
        )

    return get_iceberg_table(table_config)
