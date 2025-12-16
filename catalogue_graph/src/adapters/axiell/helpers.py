import adapters.axiell.config as config
from adapters.utils.iceberg import (
    IcebergTable,
    IcebergTableConfig,
    get_iceberg_table,
)
from adapters.utils.window_store import (
    WINDOW_STATUS_SCHEMA,
    WindowStore,
)


def build_adapter_table(
    use_rest_api_table: bool, create_if_not_exists: bool = True
) -> IcebergTable:
    if use_rest_api_table:
        table_config = IcebergTableConfig(
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            use_rest_api_table=True,
            create_if_not_exists=create_if_not_exists,
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
            create_if_not_exists=create_if_not_exists,
            db_name=config.LOCAL_DB_NAME,
        )

    return get_iceberg_table(table_config)


def build_window_status_table(
    use_rest_api_table: bool = True,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    """Load or create the Iceberg table backing the window status store."""

    if use_rest_api_table:
        table_config = IcebergTableConfig(
            table_name=config.WINDOW_STATUS_TABLE,
            namespace=config.WINDOW_STATUS_NAMESPACE,
            use_rest_api_table=True,
            create_if_not_exists=create_if_not_exists,
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
        )
    else:
        table_config = IcebergTableConfig(
            table_name=config.LOCAL_WINDOW_STATUS_TABLE,
            namespace=config.LOCAL_WINDOW_STATUS_NAMESPACE,
            use_rest_api_table=False,
            create_if_not_exists=create_if_not_exists,
            db_name=config.LOCAL_WINDOW_STATUS_DB_NAME,
        )

    return get_iceberg_table(
        table_config,
        schema=WINDOW_STATUS_SCHEMA,
        partition_spec=None,
    )


def build_window_store(*, use_rest_api_table: bool = True) -> WindowStore:
    table = build_window_status_table(use_rest_api_table=use_rest_api_table)
    return WindowStore(table)
