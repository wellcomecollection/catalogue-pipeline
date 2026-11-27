"""Runtime configuration helpers for the Axiell adapter."""

from __future__ import annotations

import os
from typing import Literal, cast, get_args

from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig
from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
)
from adapters.utils.schemata import (
    ADAPTER_STORE_ICEBERG_SCHEMA,
    ADAPTER_STORE_SORT_ORDER,
    DELETION_FACTS_ICEBERG_SCHEMA,
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
# Retries for transient failures (empty bodies, transport errors, 5xx); shares
# the backoff ladder configured by OAI_BACKOFF_FACTOR / OAI_BACKOFF_MAX.
OAI_TRANSIENT_RETRIES = int(os.getenv("OAI_TRANSIENT_RETRIES", "3"))


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

CollectionPathSource = Literal["refno", "part_of"]


def _parse_collection_path_source(value: str) -> CollectionPathSource:
    # Fail at start-up: a typo must not quietly fall back to either mode.
    if value not in get_args(CollectionPathSource):
        raise ValueError(
            f"AXIELL_COLLECTION_PATH_SOURCE must be one of "
            f"{get_args(CollectionPathSource)}, got {value!r}"
        )
    return cast(CollectionPathSource, value)


AXIELL_COLLECTION_PATH_SOURCE = _parse_collection_path_source(
    os.getenv("AXIELL_COLLECTION_PATH_SOURCE", "refno")
)


def _parse_flag(name: str, default: str) -> bool:
    value = os.getenv(name, default).strip().lower()
    if value not in ("true", "false"):
        raise ValueError(f'{name} must be "true" or "false", got {value!r}')
    return value == "true"


# Only set where FOLIO works exist: the id minter mints an id for every merge
# candidate, and FOLIO ids must stay out of production's registry.
AXIELL_FOLIO_MERGE_CANDIDATES = _parse_flag("AXIELL_FOLIO_MERGE_CANDIDATES", "false")

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
    auto_retry_failed_windows=AUTO_RETRY_FAILED_WINDOWS,
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

DELETION_FACTS_REST_API_TABLE_NAME = os.getenv(
    "DELETION_FACTS_REST_API_TABLE_NAME", "axiell_deletion_facts_table"
)
DELETION_FACTS_LOCAL_TABLE_NAME = os.getenv(
    "DELETION_FACTS_LOCAL_TABLE_NAME", "axiell_local_deletion_facts_table"
)

DELETION_FACTS_REST_API_CONFIG = RestApiIcebergTableConfig(
    table_name=DELETION_FACTS_REST_API_TABLE_NAME,
    namespace=REST_API_NAMESPACE,
    iceberg_schema=DELETION_FACTS_ICEBERG_SCHEMA,
    s3_tables_bucket=S3_TABLES_BUCKET,
    region=AWS_REGION,
    account_id=AWS_ACCOUNT_ID,
)

DELETION_FACTS_LOCAL_CONFIG = LocalIcebergTableConfig(
    table_name=DELETION_FACTS_LOCAL_TABLE_NAME,
    namespace=LOCAL_NAMESPACE,
    iceberg_schema=DELETION_FACTS_ICEBERG_SCHEMA,
    db_name=LOCAL_DB_NAME,
)

# ---------------------------------------------------------------------------
# Axiell UAT
# ---------------------------------------------------------------------------
# A second Axiell adapter that harvests the Collections UAT system into its own
# S3 Tables bucket, so that test edits never mix with live records.
# Every table, the window status namespace and the SSM parameters are separate.
# The SSM parameters sit under /catalogue_pipeline/axiell_uat, the prefix the
# adapter module's IAM policy grants for this namespace.
UAT_S3_TABLES_BUCKET = os.getenv(
    "AXIELL_UAT_S3_TABLES_BUCKET", "wellcomecollection-platform-axiell-uat-adapter"
)
UAT_REST_API_TABLE_NAME = os.getenv(
    "AXIELL_UAT_REST_API_TABLE_NAME", "axiell_uat_adapter_table"
)
UAT_WINDOW_STATUS_NAMESPACE = os.getenv(
    "AXIELL_UAT_WINDOW_STATUS_NAMESPACE", "axiell_uat_window_status"
)
UAT_RECONCILER_REST_API_TABLE_NAME = os.getenv(
    "AXIELL_UAT_RECONCILER_REST_API_TABLE_NAME", "axiell_uat_reconciler_table"
)
UAT_DELETION_FACTS_REST_API_TABLE_NAME = os.getenv(
    "AXIELL_UAT_DELETION_FACTS_REST_API_TABLE_NAME",
    "axiell_uat_deletion_facts_table",
)
UAT_LOCAL_NAMESPACE = os.getenv("AXIELL_UAT_LOCAL_NAMESPACE", "axiell_uat_local")
UAT_LOCAL_DB_NAME = os.getenv("AXIELL_UAT_LOCAL_DB_NAME", "axiell_uat_catalog")
UAT_SSM_OAI_TOKEN = os.getenv(
    "AXIELL_UAT_SSM_OAI_TOKEN", "/catalogue_pipeline/axiell_uat/oai_api_token"
)
UAT_SSM_OAI_URL = os.getenv(
    "AXIELL_UAT_SSM_OAI_URL", "/catalogue_pipeline/axiell_uat/oai_api_url"
)
# UAT sits behind IIS Windows authentication as well as the token check, so it
# also needs an NTLM login. Live needs the token only.
UAT_SSM_OAI_NTLM_USERNAME = os.getenv(
    "AXIELL_UAT_SSM_OAI_NTLM_USERNAME", "/catalogue_pipeline/axiell_uat/oai_username"
)
UAT_SSM_OAI_NTLM_PASSWORD = os.getenv(
    "AXIELL_UAT_SSM_OAI_NTLM_PASSWORD", "/catalogue_pipeline/axiell_uat/oai_password"
)
UAT_OAI_SET_SPEC = os.getenv("AXIELL_UAT_OAI_SET_SPEC", "collect")
UAT_S3_BUCKET = os.getenv(
    "AXIELL_UAT_S3_BUCKET", "wellcomecollection-platform-axiell-uat-adapter"
)

AXIELL_UAT_ADAPTER_CONFIG = AXIELL_ADAPTER_CONFIG.model_copy(
    update={
        "adapter_name": "axiell_uat",
        "adapter_namespace": "axiell_uat",
        "pipeline_step_prefix": "axiell_uat_adapter",
        "oai_set_spec": UAT_OAI_SET_SPEC,
        "rest_api_iceberg_config": AXIELL_ADAPTER_CONFIG.rest_api_iceberg_config.model_copy(
            update={
                "table_name": UAT_REST_API_TABLE_NAME,
                "s3_tables_bucket": UAT_S3_TABLES_BUCKET,
            }
        ),
        "rest_api_window_status_iceberg_config": AXIELL_ADAPTER_CONFIG.rest_api_window_status_iceberg_config.model_copy(
            update={
                "namespace": UAT_WINDOW_STATUS_NAMESPACE,
                "s3_tables_bucket": UAT_S3_TABLES_BUCKET,
            }
        ),
        "local_iceberg_config": AXIELL_ADAPTER_CONFIG.local_iceberg_config.model_copy(
            update={
                "table_name": "axiell_uat_local_table",
                "namespace": UAT_LOCAL_NAMESPACE,
                "db_name": UAT_LOCAL_DB_NAME,
            }
        ),
        "local_window_status_iceberg_config": AXIELL_ADAPTER_CONFIG.local_window_status_iceberg_config.model_copy(
            update={
                "namespace": UAT_WINDOW_STATUS_NAMESPACE,
                "db_name": "axiell_uat_window_status",
            }
        ),
        "report_s3_bucket": UAT_S3_BUCKET,
    }
)

UAT_RECONCILER_REST_API_CONFIG = RECONCILER_REST_API_CONFIG.model_copy(
    update={
        "table_name": UAT_RECONCILER_REST_API_TABLE_NAME,
        "s3_tables_bucket": UAT_S3_TABLES_BUCKET,
    }
)
UAT_RECONCILER_LOCAL_CONFIG = RECONCILER_LOCAL_CONFIG.model_copy(
    update={
        "table_name": "axiell_uat_local_reconciler_table",
        "namespace": UAT_LOCAL_NAMESPACE,
        "db_name": UAT_LOCAL_DB_NAME,
    }
)
UAT_DELETION_FACTS_REST_API_CONFIG = DELETION_FACTS_REST_API_CONFIG.model_copy(
    update={
        "table_name": UAT_DELETION_FACTS_REST_API_TABLE_NAME,
        "s3_tables_bucket": UAT_S3_TABLES_BUCKET,
    }
)
UAT_DELETION_FACTS_LOCAL_CONFIG = DELETION_FACTS_LOCAL_CONFIG.model_copy(
    update={
        "table_name": "axiell_uat_local_deletion_facts_table",
        "namespace": UAT_LOCAL_NAMESPACE,
        "db_name": UAT_LOCAL_DB_NAME,
    }
)
