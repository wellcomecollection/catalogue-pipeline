import os

# AWS Configuration
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

# Glue Table Configuration
S3_TABLES_BUCKET = os.getenv(
    "S3_TABLES_BUCKET", "wellcomecollection-platform-ebsco-adapter"
)
GLUE_TABLE_NAME = os.getenv("GLUE_TABLE_NAME", "ebsco_adapter_table")
GLUE_NAMESPACE = os.getenv("GLUE_NAMESPACE", "wellcomecollection_catalogue")

# Local Table Configuration
LOCAL_TABLE_NAME = os.getenv("LOCAL_TABLE_NAME", "mytable")
LOCAL_NAMESPACE = os.getenv("LOCAL_NAMESPACE", "default")
LOCAL_DB_NAME = os.getenv("LOCAL_DB_NAME", "catalog")


# Local Elasticsearch Configuration
ES_LOCAL_HOST = os.environ.get("ES_LOCAL_HOST", "localhost")
ES_LOCAL_PORT = int(os.environ.get("ES_LOCAL_PORT", "9200"))
ES_LOCAL_SCHEME = os.environ.get("ES_LOCAL_SCHEME", "http")
ES_LOCAL_API_KEY = os.environ.get("ES_LOCAL_API_KEY")

PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
INDEX_DATE = os.getenv("INDEX_DATE")  # New optional variable for index date

# EBSCO Adapter Trigger Configuration (moved from steps/trigger.py)
SSM_PARAM_PREFIX = "/catalogue_pipeline/ebsco_adapter"
S3_BUCKET = os.getenv("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
S3_PREFIX = os.getenv("S3_PREFIX", "dev")  # corresponds to previous s3_prefix
FTP_S3_PREFIX = os.path.join(S3_PREFIX, "ftp_v2")
