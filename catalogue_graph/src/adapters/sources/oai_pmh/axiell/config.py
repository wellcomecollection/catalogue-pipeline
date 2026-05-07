"""Runtime configuration helpers for the Axiell adapter."""

from __future__ import annotations

import os

from adapters.sources.oai_pmh.runtime import OAIPMHAdapterConfig
from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
)
from adapters.utils.schemata import (
    ADAPTER_STORE_ICEBERG_SCHEMA,
    ADAPTER_STORE_SORT_ORDER,
    RECONCILER_STORE_ICEBERG_SCHEMA,
)
from adapters.utils.window_store import WINDOW_STATUS_SCHEMA

# ---------------------------------------------------------------------------
# AWS account / region context
# ---------------------------------------------------------------------------
AWS_REGION = os.getenv("AWS_REGION", "eu-west-1")
AWS_ACCOUNT_ID = os.getenv("AWS_ACCOUNT_ID")


# ---------------------------------------------------------------------------
# Iceberg catalogue defaults
# ---------------------------------------------------------------------------
S3_TABLES_BUCKET = os.getenv(
    "AXIELL_S3_TABLES_BUCKET", "wellcomecollection-platform-axiell-adapter"
)
REST_API_TABLE_NAME = os.getenv("REST_API_TABLE_NAME", "axiell_adapter_table")
REST_API_NAMESPACE = os.getenv("REST_API_NAMESPACE", "wellcomecollection_catalogue")

WINDOW_STATUS_NAMESPACE = os.getenv("WINDOW_STATUS_NAMESPACE", "axiell_window_status")
WINDOW_STATUS_TABLE = os.getenv("WINDOW_STATUS_TABLE", "window_status")
WINDOW_STATUS_CATALOG_NAME = os.getenv(
    "WINDOW_STATUS_CATALOG_NAME", "axiell_window_status_catalog"
)

# ---------------------------------------------------------------------------
# Local Table Configuration (development/testing)
# ---------------------------------------------------------------------------
LOCAL_TABLE_NAME = os.getenv("LOCAL_TABLE_NAME", "axiell_local_table")
LOCAL_NAMESPACE = os.getenv("LOCAL_NAMESPACE", "axiell_local")
LOCAL_DB_NAME = os.getenv("LOCAL_DB_NAME", "axiell_catalog")
LOCAL_WINDOW_STATUS_TABLE = os.getenv("LOCAL_WINDOW_STATUS_TABLE", WINDOW_STATUS_TABLE)
LOCAL_WINDOW_STATUS_NAMESPACE = os.getenv(
    "LOCAL_WINDOW_STATUS_NAMESPACE", WINDOW_STATUS_NAMESPACE
)
LOCAL_WINDOW_STATUS_DB_NAME = os.getenv(
    "LOCAL_WINDOW_STATUS_DB_NAME", "axiell_window_status"
)
LOCAL_WINDOW_STATUS_CATALOG_NAME = os.getenv(
    "LOCAL_WINDOW_STATUS_CATALOG_NAME", f"{WINDOW_STATUS_CATALOG_NAME}_local"
)

# ---------------------------------------------------------------------------
# OAI-PMH connectivity
# ---------------------------------------------------------------------------
SSM_OAI_TOKEN = os.getenv(
    "SSM_OAI_TOKEN", "/catalogue_pipeline/axiell_collections/oai_api_token"
)
SSM_OAI_URL = os.getenv(
    "SSM_OAI_URL", "/catalogue_pipeline/axiell_collections/oai_api_url"
)
OAI_SET_SPEC = os.getenv("OAI_SET_SPEC", "collect")
OAI_METADATA_PREFIX = os.getenv("OAI_METADATA_PREFIX", "oai_marcxml")
OAI_HTTP_TIMEOUT = float(os.getenv("OAI_HTTP_TIMEOUT", "10.0"))
OAI_MAX_READ_TIMEOUT = float(os.getenv("OAI_MAX_READ_TIMEOUT", "60.0"))
OAI_MAX_RETRIES = int(os.getenv("OAI_MAX_RETRIES", "1"))
OAI_BACKOFF_FACTOR = float(os.getenv("OAI_BACKOFF_FACTOR", "0.75"))
OAI_BACKOFF_MAX = float(os.getenv("OAI_BACKOFF_MAX", "5.0"))


# ---------------------------------------------------------------------------
# Window harvesting behaviour
# ---------------------------------------------------------------------------
WINDOW_MINUTES = int(os.getenv("WINDOW_MINUTES", "15"))
WINDOW_LOOKBACK_DAYS = int(os.getenv("WINDOW_LOOKBACK_DAYS", "7"))
_MAX_PENDING_WINDOWS_ENV = os.getenv("MAX_PENDING_WINDOWS")
MAX_PENDING_WINDOWS: int | None = (
    int(_MAX_PENDING_WINDOWS_ENV) if _MAX_PENDING_WINDOWS_ENV is not None else None
)

MAX_LAG_MINUTES = int(os.getenv("MAX_LAG_MINUTES", "360"))
AUTO_RETRY_FAILED_WINDOWS = (
    os.getenv("AUTO_RETRY_FAILED_WINDOWS", "true").lower() == "true"
)


# ---------------------------------------------------------------------------
# EventBridge integration
# ---------------------------------------------------------------------------
EVENT_BUS_NAME = os.getenv("EVENT_BUS_NAME", "catalogue-pipeline-events")
TRIGGER_DETAIL_TYPE = os.getenv("TRIGGER_DETAIL_TYPE", "AxiellWindowRequested")
LOADER_DETAIL_TYPE = os.getenv("LOADER_DETAIL_TYPE", "AxiellWindowLoaded")


# ---------------------------------------------------------------------------
# Chatbot notifications
# ---------------------------------------------------------------------------
CHATBOT_TOPIC_ARN = os.getenv("CHATBOT_TOPIC_ARN")


# ---------------------------------------------------------------------------
# Elasticsearch / downstream transform config
# ---------------------------------------------------------------------------
PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
INDEX_DATE = os.getenv("INDEX_DATE", "2026-01-12")  # Use a non-production index for now
ES_API_KEY_NAME = os.getenv("ES_API_KEY_NAME", "transformer_axiell")
ES_INDEX_NAME = os.getenv("ES_INDEX_NAME", "works-source")
ES_MODE = os.getenv("ES_MODE", "private")

# Manifest storage configuration
S3_BUCKET = os.getenv(
    "S3_BUCKET",
    "wellcomecollection-platform-axiell-adapter",
)
S3_PREFIX = os.getenv("S3_PREFIX", "dev")

# ---------------------------------------------------------------------------
# OAI-PMH Adapter Config (Pydantic model for runtime)
# ---------------------------------------------------------------------------
AXIELL_ADAPTER_CONFIG = OAIPMHAdapterConfig(
    # Identity
    adapter_name="axiell",
    adapter_namespace="axiell",
    pipeline_step_prefix="axiell_adapter",
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
    # Rest API Iceberg tables
    rest_api_iceberg_config=RestApiIcebergTableConfig(
        table_name=REST_API_TABLE_NAME,
        namespace=REST_API_NAMESPACE,
        iceberg_schema=ADAPTER_STORE_ICEBERG_SCHEMA,
        sort_order=ADAPTER_STORE_SORT_ORDER,
        s3_tables_bucket=S3_TABLES_BUCKET,
        region=AWS_REGION,
        account_id=AWS_ACCOUNT_ID,
    ),
    rest_api_window_status_iceberg_config=RestApiIcebergTableConfig(
        table_name=WINDOW_STATUS_TABLE,
        namespace=WINDOW_STATUS_NAMESPACE,
        iceberg_schema=WINDOW_STATUS_SCHEMA,
        s3_tables_bucket=S3_TABLES_BUCKET,
        region=AWS_REGION,
        account_id=AWS_ACCOUNT_ID,
    ),
    # Local Iceberg tables
    local_iceberg_config=LocalIcebergTableConfig(
        table_name=LOCAL_TABLE_NAME,
        namespace=LOCAL_NAMESPACE,
        iceberg_schema=ADAPTER_STORE_ICEBERG_SCHEMA,
        sort_order=ADAPTER_STORE_SORT_ORDER,
        db_name=LOCAL_DB_NAME,
    ),
    local_window_status_iceberg_config=LocalIcebergTableConfig(
        table_name=LOCAL_WINDOW_STATUS_TABLE,
        namespace=LOCAL_WINDOW_STATUS_NAMESPACE,
        iceberg_schema=WINDOW_STATUS_SCHEMA,
        db_name=LOCAL_WINDOW_STATUS_DB_NAME,
    ),
    # Reporting
    report_s3_bucket=S3_BUCKET,
    report_s3_prefix=S3_PREFIX,
)

RECONCILER_REST_API_TABLE_NAME = os.getenv(
    "RECONCILER_REST_API_TABLE_NAME", "axiell_reconciler_table"
)
RECONCILER_LOCAL_TABLE_NAME = os.getenv(
    "RECONCILER_LOCAL_TABLE_NAME", "axiell_local_reconciler_table"
)

RECONCILER_REST_API_CONFIG = RestApiIcebergTableConfig(
    table_name=RECONCILER_REST_API_TABLE_NAME,
    namespace=REST_API_NAMESPACE,
    iceberg_schema=RECONCILER_STORE_ICEBERG_SCHEMA,
    s3_tables_bucket=S3_TABLES_BUCKET,
    region=AWS_REGION,
    account_id=AWS_ACCOUNT_ID,
)

RECONCILER_LOCAL_CONFIG = LocalIcebergTableConfig(
    table_name=RECONCILER_LOCAL_TABLE_NAME,
    namespace=LOCAL_NAMESPACE,
    iceberg_schema=RECONCILER_STORE_ICEBERG_SCHEMA,
    db_name=LOCAL_DB_NAME,
)
