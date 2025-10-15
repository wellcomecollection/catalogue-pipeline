import os

# AWS Configuration
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

# Iceberg REST API Table Configuration
S3_TABLES_BUCKET = os.getenv(
    "S3_TABLES_BUCKET", "wellcomecollection-platform-ebsco-adapter"
)
REST_API_TABLE_NAME = os.getenv("REST_API_TABLE_NAME", "ebsco_adapter_table")
REST_API_NAMESPACE = os.getenv("REST_API_NAMESPACE", "wellcomecollection_catalogue")

# Local Table Configuration
LOCAL_TABLE_NAME = os.getenv("LOCAL_TABLE_NAME", "mytable")
LOCAL_NAMESPACE = os.getenv("LOCAL_NAMESPACE", "default")
LOCAL_DB_NAME = os.getenv("LOCAL_DB_NAME", "catalog")

# Adapter Trigger Configuration (moved from steps/trigger.py)
SSM_PARAM_PREFIX = "/catalogue_pipeline/ebsco_adapter"
S3_BUCKET = os.getenv("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
S3_PREFIX = os.getenv("S3_PREFIX", "dev")  # corresponds to previous s3_prefix
FTP_S3_PREFIX = os.path.join(S3_PREFIX, "ftp_v2")
BATCH_S3_PREFIX = os.path.join(S3_PREFIX, "batches")

# Transform Configuration
PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
INDEX_DATE = os.getenv("INDEX_DATE")
ES_API_KEY_NAME = os.getenv("ES_API_KEY_NAME", "transformer")
