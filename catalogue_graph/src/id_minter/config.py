"""Runtime configuration for the ID Minter service.

Mirrors the Scala IdMinterConfig / RDSClientConfig / IdentifiersTableConfig
hierarchy, sourced from environment variables with sensible defaults.
"""

from __future__ import annotations

import os

from pydantic import BaseModel

import utils.load_env  # noqa: F401

# ---------------------------------------------------------------------------
# RDS connectivity (identifier store)
# ---------------------------------------------------------------------------
RDS_PRIMARY_HOST = os.getenv("RDS_PRIMARY_HOST", "localhost")
RDS_REPLICA_HOST = os.getenv("RDS_REPLICA_HOST", RDS_PRIMARY_HOST)
RDS_PORT = int(os.getenv("RDS_PORT", "3306"))
RDS_USERNAME = os.getenv("RDS_USERNAME", "id_minter")
RDS_PASSWORD = os.getenv("RDS_PASSWORD", "id_minter")
RDS_MAX_CONNECTIONS = int(os.getenv("RDS_MAX_CONNECTIONS", "8"))

# ---------------------------------------------------------------------------
# Identifiers database and tables
# ---------------------------------------------------------------------------
IDENTIFIERS_DATABASE = os.getenv("IDENTIFIERS_DATABASE", "identifiers")
IDENTIFIERS_TABLE_NAME = os.getenv("IDENTIFIERS_TABLE_NAME", "identifiers")
CANONICAL_IDS_TABLE_NAME = os.getenv("CANONICAL_IDS_TABLE_NAME", "canonical_ids")

# ---------------------------------------------------------------------------
# Elasticsearch indices
# ---------------------------------------------------------------------------
ES_SOURCE_INDEX_PREFIX = os.getenv("ES_SOURCE_INDEX_PREFIX", "works-source")
ES_TARGET_INDEX_PREFIX = os.getenv("ES_TARGET_INDEX_PREFIX", "works-identified")

# ---------------------------------------------------------------------------
# Downstream notification target
# ---------------------------------------------------------------------------
DOWNSTREAM_SNS_TOPIC_ARN = os.getenv("DOWNSTREAM_SNS_TOPIC_ARN")

# ---------------------------------------------------------------------------
# S3 manifest output
# ---------------------------------------------------------------------------
S3_BUCKET = os.getenv("S3_BUCKET", "wellcomecollection-platform-id-minter")
S3_PREFIX = os.getenv("S3_PREFIX", "dev")
BATCH_S3_PREFIX = os.path.join(S3_PREFIX, "id_minter")

# ---------------------------------------------------------------------------
# RDS Data API (for local/CLI access without direct DB connectivity)
# ---------------------------------------------------------------------------
RDS_CLUSTER_ID = os.getenv("RDS_CLUSTER_ID", "identifiers-v2-serverless")
RDS_REGION = os.getenv("RDS_REGION", "eu-west-1")

# ---------------------------------------------------------------------------
# General
# ---------------------------------------------------------------------------
PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
ES_SOURCE_INDEX_DATE_SUFFIX = os.getenv("ES_SOURCE_INDEX_DATE_SUFFIX")
ES_TARGET_INDEX_DATE_SUFFIX = os.getenv("ES_TARGET_INDEX_DATE_SUFFIX")
APPLY_MIGRATIONS = os.getenv("APPLY_MIGRATIONS", "false").lower() in (
    "true",
    "1",
    "yes",
)
ID_GENERATOR_DESIRED_FREE_IDS_COUNT = int(
    os.getenv("ID_GENERATOR_DESIRED_FREE_IDS_COUNT", "100000")
)


# ---------------------------------------------------------------------------
# Pydantic config models (mirrors Scala case classes)
# ---------------------------------------------------------------------------
class RDSClientConfig(BaseModel):
    primary_host: str = RDS_PRIMARY_HOST
    replica_host: str = RDS_REPLICA_HOST
    port: int = RDS_PORT
    username: str = RDS_USERNAME
    password: str = RDS_PASSWORD
    max_connections: int = RDS_MAX_CONNECTIONS


class DBConfig(BaseModel):
    """Base config for database access."""

    rds_client: RDSClientConfig = RDSClientConfig()
    db_name: str = IDENTIFIERS_DATABASE
    apply_migrations: bool = APPLY_MIGRATIONS


class IdMinterConfig(DBConfig):
    db_table: str = IDENTIFIERS_TABLE_NAME
    source_index_prefix: str = ES_SOURCE_INDEX_PREFIX
    target_index_prefix: str = ES_TARGET_INDEX_PREFIX
    downstream_sns_topic_arn: str | None = DOWNSTREAM_SNS_TOPIC_ARN
    pipeline_date: str = PIPELINE_DATE
    source_index_date_suffix: str | None = ES_SOURCE_INDEX_DATE_SUFFIX
    target_index_date_suffix: str | None = ES_TARGET_INDEX_DATE_SUFFIX
    rds_cluster_id: str = RDS_CLUSTER_ID
    rds_region: str = RDS_REGION
    s3_bucket: str = S3_BUCKET
    batch_s3_prefix: str = BATCH_S3_PREFIX

    @property
    def source_index_name(self) -> str:
        date = self.source_index_date_suffix or self.pipeline_date
        return f"{self.source_index_prefix}-{date}"

    @property
    def target_index_name(self) -> str:
        date = self.target_index_date_suffix or self.pipeline_date
        return f"{self.target_index_prefix}-{date}"


# Default id_minter config instance, built from environment variables.
ID_MINTER_CONFIG = IdMinterConfig()


class IdGeneratorConfig(DBConfig):
    db_table: str = CANONICAL_IDS_TABLE_NAME
    pipeline_date: str = PIPELINE_DATE
    desired_free_ids_count: int = ID_GENERATOR_DESIRED_FREE_IDS_COUNT


# Default id_generator config instance, built from environment variables.
ID_GENERATOR_CONFIG = IdGeneratorConfig()
