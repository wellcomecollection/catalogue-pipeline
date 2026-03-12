import os

from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
)
from adapters.utils.schemata import ADAPTER_STORE_ICEBERG_SCHEMA

# AWS Configuration
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

# Adapter Trigger Configuration (moved from steps/trigger.py)
SSM_PARAM_PREFIX = "/catalogue_pipeline/ebsco_adapter"
S3_BUCKET = os.getenv("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
S3_PREFIX = os.getenv("S3_PREFIX", "dev")
FTP_S3_PREFIX = os.path.join(S3_PREFIX, "ftp_v2")

# Transform Configuration
PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
INDEX_DATE = os.getenv("INDEX_DATE")
ES_INDEX_NAME = os.getenv("ES_INDEX_NAME", "works-source")
ES_API_KEY_NAME = os.getenv("ES_API_KEY_NAME", "transformer")


# Iceberg REST API Table Configuration
REST_API_ICEBERG_CONFIG = RestApiIcebergTableConfig(
    table_name=os.getenv("REST_API_TABLE_NAME", "ebsco_adapter_table"),
    namespace=os.getenv("REST_API_NAMESPACE", "wellcomecollection_catalogue"),
    iceberg_schema=ADAPTER_STORE_ICEBERG_SCHEMA,
    s3_tables_bucket=os.getenv(
        "S3_TABLES_BUCKET", "wellcomecollection-platform-ebsco-adapter"
    ),
    region=AWS_REGION,
    account_id=AWS_ACCOUNT_ID,
)

LOCAL_ICEBERG_CONFIG = LocalIcebergTableConfig(
    table_name=os.getenv("LOCAL_TABLE_NAME", "mytable"),
    namespace=os.getenv("LOCAL_NAMESPACE", "default"),
    iceberg_schema=ADAPTER_STORE_ICEBERG_SCHEMA,
    db_name=os.getenv("LOCAL_DB_NAME", "catalog"),
)
