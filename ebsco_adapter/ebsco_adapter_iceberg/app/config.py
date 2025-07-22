import os

# AWS Configuration
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")

# Glue Table Configuration
S3_TABLES_BUCKET = os.getenv("S3_TABLES_BUCKET", "wellcomecollection-platform-ebsco-adapter")
GLUE_TABLE_NAME = os.getenv("GLUE_TABLE_NAME", "ebsco_adapter_table")
GLUE_NAMESPACE = os.getenv("GLUE_NAMESPACE", "wellcomecollection_catalogue")

# Local Table Configuration
LOCAL_TABLE_NAME = os.getenv("LOCAL_TABLE_NAME", "mytable")
LOCAL_NAMESPACE = os.getenv("LOCAL_NAMESPACE", "default")
LOCAL_DB_NAME = os.getenv("LOCAL_DB_NAME", "catalog")
