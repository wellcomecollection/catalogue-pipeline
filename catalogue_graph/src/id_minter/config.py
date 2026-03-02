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
RDS_PASSWORD = os.getenv("RDS_PASSWORD", "")
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
ES_SOURCE_INDEX = os.getenv("ES_SOURCE_INDEX", "works-source")
ES_TARGET_INDEX = os.getenv("ES_TARGET_INDEX", "works-identified")

# ---------------------------------------------------------------------------
# Downstream notification target
# ---------------------------------------------------------------------------
DOWNSTREAM_SNS_TOPIC_ARN = os.getenv("DOWNSTREAM_SNS_TOPIC_ARN")

# ---------------------------------------------------------------------------
# General
# ---------------------------------------------------------------------------
PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")
APPLY_MIGRATIONS = os.getenv("APPLY_MIGRATIONS", "false").lower() in (
    "true",
    "1",
    "yes",
)
IDS_GENERATOR_DESIRED_FREE_IDS_COUNT = int(
    os.getenv("IDS_GENERATOR_DESIRED_FREE_IDS_COUNT", "1000")
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


class IdentifiersTableConfig(BaseModel):
    database: str = IDENTIFIERS_DATABASE
    table_name: str = IDENTIFIERS_TABLE_NAME


class IdMinterConfig(BaseModel):
    rds_client: RDSClientConfig = RDSClientConfig()
    db_table: IdentifiersTableConfig = IdentifiersTableConfig()
    source_index: str = ES_SOURCE_INDEX
    target_index: str = ES_TARGET_INDEX
    downstream_sns_topic_arn: str | None = DOWNSTREAM_SNS_TOPIC_ARN
    pipeline_date: str = PIPELINE_DATE
    apply_migrations: bool = APPLY_MIGRATIONS


# Default id_minter config instance, built from environment variables.
ID_MINTER_CONFIG = IdMinterConfig()


class CanonicalIdsTableConfig(BaseModel):
    database: str = IDENTIFIERS_DATABASE
    table_name: str = CANONICAL_IDS_TABLE_NAME


class IdGeneratorConfig(BaseModel):
    rds_client: RDSClientConfig = RDSClientConfig()
    db_table: CanonicalIdsTableConfig = CanonicalIdsTableConfig()
    pipeline_date: str = PIPELINE_DATE
    apply_migrations: bool = APPLY_MIGRATIONS


# Default id_generator config instance, built from environment variables.
ID_GENERATOR_CONFIG = IdGeneratorConfig()
