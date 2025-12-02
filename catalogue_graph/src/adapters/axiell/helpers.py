import adapters.axiell.config as config
from adapters.utils.iceberg import (
    IcebergTable,
    IcebergTableConfig,
    get_iceberg_table,
)
from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    IcebergWindowStore,
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


def load_window_status_table(
    *,
    create_if_not_exists: bool = True,
    use_rest_api_table: bool = True,
) -> IcebergTable:
    """Load or create the Iceberg table backing the window status store."""

    if use_rest_api_table:
        table_config = IcebergTableConfig(
            table_name=config.WINDOW_STATUS_TABLE,
            namespace=config.WINDOW_STATUS_NAMESPACE,
            use_rest_api_table=True,
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
            create_if_not_exists=create_if_not_exists,
        )
    else:
        table_config = IcebergTableConfig(
            table_name=config.LOCAL_WINDOW_STATUS_TABLE,
            namespace=config.LOCAL_WINDOW_STATUS_NAMESPACE,
            use_rest_api_table=False,
            db_name=config.LOCAL_WINDOW_STATUS_DB_NAME,
            create_if_not_exists=create_if_not_exists,
        )

    return get_iceberg_table(
        table_config,
        schema=WINDOW_STATUS_SCHEMA,
        partition_spec=WINDOW_STATUS_PARTITION_SPEC,
    )


def build_window_store(*, use_rest_api_table: bool = True) -> IcebergWindowStore:
    table = load_window_status_table(use_rest_api_table=use_rest_api_table)
    return IcebergWindowStore(table)
