import os

from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
    get_local_table,
    get_rest_api_table,
)
from merger.schemata import (
    WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    WORKS_IDENTIFIED_SORT_ORDER,
)

AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

REST_API_ICEBERG_CONFIG = RestApiIcebergTableConfig(
    table_name=os.getenv("WORKS_IDENTIFIED_TABLE_NAME", "works_identified"),
    namespace=os.getenv("WORKS_IDENTIFIED_NAMESPACE", "wellcomecollection_catalogue"),
    iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    sort_order=WORKS_IDENTIFIED_SORT_ORDER,
    s3_tables_bucket=os.getenv(
        "S3_TABLES_BUCKET", "wellcomecollection-platform-catalogue-pipeline"
    ),
    region=AWS_REGION,
    account_id=AWS_ACCOUNT_ID,
)

LOCAL_ICEBERG_CONFIG = LocalIcebergTableConfig(
    table_name=os.getenv("LOCAL_WORKS_IDENTIFIED_TABLE_NAME", "works_identified"),
    namespace=os.getenv("LOCAL_WORKS_IDENTIFIED_NAMESPACE", "matcher"),
    iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    sort_order=WORKS_IDENTIFIED_SORT_ORDER,
    db_name=os.getenv("LOCAL_DB_NAME", "matcher_catalog"),
)


def get_works_identified_table(use_rest_api_table: bool) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(REST_API_ICEBERG_CONFIG, create_if_not_exists=False)

    return get_local_table(LOCAL_ICEBERG_CONFIG, create_if_not_exists=False)
