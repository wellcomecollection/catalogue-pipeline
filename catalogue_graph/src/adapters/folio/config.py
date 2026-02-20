"""Runtime configuration for the FOLIO adapter."""

from __future__ import annotations

import os

from adapters.oai_pmh.runtime import OAIPMHAdapterConfig

# ---------------------------------------------------------------------------
# AWS account / region context
# ---------------------------------------------------------------------------
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")


# ---------------------------------------------------------------------------
# Iceberg catalogue defaults
# ---------------------------------------------------------------------------
S3_TABLES_BUCKET = os.getenv(
    "FOLIO_S3_TABLES_BUCKET", "wellcomecollection-platform-folio-adapter"
)
REST_API_TABLE_NAME = os.getenv("FOLIO_REST_API_TABLE_NAME", "folio_adapter_table")
REST_API_NAMESPACE = os.getenv(
    "FOLIO_REST_API_NAMESPACE", "wellcomecollection_catalogue"
)

WINDOW_STATUS_NAMESPACE = os.getenv(
    "FOLIO_WINDOW_STATUS_NAMESPACE", "folio_window_status"
)
WINDOW_STATUS_TABLE = os.getenv("FOLIO_WINDOW_STATUS_TABLE", "window_status")
WINDOW_STATUS_CATALOG_NAME = os.getenv(
    "FOLIO_WINDOW_STATUS_CATALOG_NAME", "folio_window_status_catalog"
)

# ---------------------------------------------------------------------------
# Local Table Configuration (development/testing)
# ---------------------------------------------------------------------------
LOCAL_TABLE_NAME = os.getenv("FOLIO_LOCAL_TABLE_NAME", "folio_local_table")
LOCAL_NAMESPACE = os.getenv("FOLIO_LOCAL_NAMESPACE", "folio_local")
LOCAL_DB_NAME = os.getenv("FOLIO_LOCAL_DB_NAME", "folio_catalog")
LOCAL_WINDOW_STATUS_TABLE = os.getenv(
    "FOLIO_LOCAL_WINDOW_STATUS_TABLE", WINDOW_STATUS_TABLE
)
LOCAL_WINDOW_STATUS_NAMESPACE = os.getenv(
    "FOLIO_LOCAL_WINDOW_STATUS_NAMESPACE", WINDOW_STATUS_NAMESPACE
)
LOCAL_WINDOW_STATUS_DB_NAME = os.getenv(
    "FOLIO_LOCAL_WINDOW_STATUS_DB_NAME", "folio_window_status"
)
LOCAL_WINDOW_STATUS_CATALOG_NAME = os.getenv(
    "FOLIO_LOCAL_WINDOW_STATUS_CATALOG_NAME", f"{WINDOW_STATUS_CATALOG_NAME}_local"
)

# ---------------------------------------------------------------------------
# OAI-PMH connectivity
# ---------------------------------------------------------------------------
SSM_OAI_TOKEN = os.getenv(
    "FOLIO_SSM_OAI_TOKEN", "/catalogue_pipeline/folio/oai_api_token"
)
SSM_OAI_URL = os.getenv("FOLIO_SSM_OAI_URL", "/catalogue_pipeline/folio/oai_api_url")
# FOLIO-specific: no set spec, uses marc21_withholdings prefix
OAI_SET_SPEC: str | None = os.getenv("FOLIO_OAI_SET_SPEC") or None
OAI_METADATA_PREFIX = os.getenv("FOLIO_OAI_METADATA_PREFIX", "marc21_withholdings")
OAI_HTTP_TIMEOUT = float(os.getenv("FOLIO_OAI_HTTP_TIMEOUT", "10.0"))
OAI_MAX_READ_TIMEOUT = float(os.getenv("FOLIO_OAI_MAX_READ_TIMEOUT", "60.0"))
OAI_MAX_RETRIES = int(os.getenv("FOLIO_OAI_MAX_RETRIES", "1"))
OAI_BACKOFF_FACTOR = float(os.getenv("FOLIO_OAI_BACKOFF_FACTOR", "0.75"))
OAI_BACKOFF_MAX = float(os.getenv("FOLIO_OAI_BACKOFF_MAX", "5.0"))


# ---------------------------------------------------------------------------
# Window harvesting behaviour
# ---------------------------------------------------------------------------
WINDOW_MINUTES = int(os.getenv("FOLIO_WINDOW_MINUTES", "15"))
WINDOW_LOOKBACK_DAYS = int(os.getenv("FOLIO_WINDOW_LOOKBACK_DAYS", "7"))
_MAX_PENDING_WINDOWS_ENV = os.getenv("FOLIO_MAX_PENDING_WINDOWS")
MAX_PENDING_WINDOWS: int | None = (
    int(_MAX_PENDING_WINDOWS_ENV) if _MAX_PENDING_WINDOWS_ENV is not None else None
)

MAX_LAG_MINUTES = int(os.getenv("FOLIO_MAX_LAG_MINUTES", "360"))
AUTO_RETRY_FAILED_WINDOWS = (
    os.getenv("FOLIO_AUTO_RETRY_FAILED_WINDOWS", "true").lower() == "true"
)


# ---------------------------------------------------------------------------
# EventBridge integration
# ---------------------------------------------------------------------------
EVENT_BUS_NAME = os.getenv("FOLIO_EVENT_BUS_NAME", "catalogue-pipeline-events")
TRIGGER_DETAIL_TYPE = os.getenv("FOLIO_TRIGGER_DETAIL_TYPE", "FolioWindowRequested")
LOADER_DETAIL_TYPE = os.getenv("FOLIO_LOADER_DETAIL_TYPE", "FolioWindowLoaded")


# ---------------------------------------------------------------------------
# Chatbot notifications
# ---------------------------------------------------------------------------
CHATBOT_TOPIC_ARN = os.getenv("FOLIO_CHATBOT_TOPIC_ARN")

# ---------------------------------------------------------------------------
# S3 storage
# ---------------------------------------------------------------------------
S3_BUCKET = os.getenv("S3_BUCKET", "wellcomecollection-platform-folio-adapter")
S3_PREFIX = os.getenv("S3_PREFIX", "dev")


# ---------------------------------------------------------------------------
# OAI-PMH Adapter Config (Pydantic model for runtime)
# ---------------------------------------------------------------------------
FOLIO_ADAPTER_CONFIG = OAIPMHAdapterConfig(
    # Identity
    adapter_name="folio",
    adapter_namespace="folio",
    pipeline_step_prefix="folio_adapter",
    # Window harvesting
    window_minutes=WINDOW_MINUTES,
    window_lookback_days=WINDOW_LOOKBACK_DAYS,
    max_lag_minutes=MAX_LAG_MINUTES,
    max_pending_windows=MAX_PENDING_WINDOWS,
    # OAI-PMH
    oai_metadata_prefix=OAI_METADATA_PREFIX,
    oai_set_spec=OAI_SET_SPEC,
    # Notifications
    chatbot_topic_arn=CHATBOT_TOPIC_ARN,
    # Tables - REST API
    s3_tables_bucket=S3_TABLES_BUCKET,
    rest_api_table_name=REST_API_TABLE_NAME,
    rest_api_namespace=REST_API_NAMESPACE,
    window_status_table=WINDOW_STATUS_TABLE,
    window_status_namespace=WINDOW_STATUS_NAMESPACE,
    aws_region=AWS_REGION,
    aws_account_id=AWS_ACCOUNT_ID,
    # Tables - Local
    local_db_name=LOCAL_DB_NAME,
    local_table_name=LOCAL_TABLE_NAME,
    local_namespace=LOCAL_NAMESPACE,
    local_window_status_db_name=LOCAL_WINDOW_STATUS_DB_NAME,
    local_window_status_table=LOCAL_WINDOW_STATUS_TABLE,
    local_window_status_namespace=LOCAL_WINDOW_STATUS_NAMESPACE,
    # Reporting
    report_s3_bucket=S3_BUCKET,
    report_s3_prefix=S3_PREFIX,
)
