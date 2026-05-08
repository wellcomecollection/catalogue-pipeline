import os

from pydantic import ConfigDict

from adapters.models.config import AdapterConfig
from adapters.utils.iceberg import (
    LocalIcebergTableConfig,
    RestApiIcebergTableConfig,
)
from adapters.utils.schemata import (
    ADAPTER_STORE_ICEBERG_SCHEMA,
    ADAPTER_STORE_SORT_ORDER,
)

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
    sort_order=ADAPTER_STORE_SORT_ORDER,
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
    sort_order=ADAPTER_STORE_SORT_ORDER,
    db_name=os.getenv("LOCAL_DB_NAME", "catalog"),
)


class EBSCOAdapterConfig(AdapterConfig):
    """Configuration for the EBSCO adapter.

    Extends the base AdapterConfig with EBSCO-specific configuration.
    EBSCO adapters do not use window status tracking (unlike OAI-PMH adapters).
    """

    model_config = ConfigDict(frozen=True)


# Singleton instance of the EBSCO configuration
EBSCO_ADAPTER_CONFIG = EBSCOAdapterConfig(
    adapter_name="ebsco",
    adapter_namespace=os.getenv("ADAPTER_NAMESPACE", "ebsco"),
    pipeline_step_prefix=os.getenv("PIPELINE_STEP_PREFIX", "ebsco_adapter"),
    local_iceberg_config=LOCAL_ICEBERG_CONFIG,
    rest_api_iceberg_config=REST_API_ICEBERG_CONFIG,
)


class EBSCORuntimeConfig:
    """EBSCO adapter runtime configuration.

    Provides a compatible interface with OAI-PMH adapter runtime classes
    (AXIELL_CONFIG, FOLIO_CONFIG) so they can be used interchangeably
    in code that expects a .config property.
    """

    def __init__(self, config: EBSCOAdapterConfig | None = None):
        self._config = config or EBSCO_ADAPTER_CONFIG

    @property
    def config(self) -> EBSCOAdapterConfig:
        """Return the adapter configuration."""
        return self._config


# Singleton instance for use by lambda handlers and notebooks
EBSCO_CONFIG = EBSCORuntimeConfig()
