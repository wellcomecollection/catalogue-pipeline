"""Database access layer for the ID Minter.

Provides connection management and yoyo migration support for the
identifiers database — following the schema defined in RFC 083.
"""

from __future__ import annotations

from pathlib import Path

import pymysql
import pymysql.cursors
import structlog
from yoyo import get_backend, read_migrations

from id_minter.config import IdMinterConfig

logger = structlog.get_logger(__name__)

MIGRATIONS_DIR = str(Path(__file__).parent / "migrations")


def get_connection(config: IdMinterConfig) -> pymysql.connections.Connection:
    """Open a new pymysql connection using the ID Minter config."""
    return pymysql.connect(
        host=config.rds_client.primary_host,
        port=config.rds_client.port,
        user=config.rds_client.username,
        password=config.rds_client.password,
        database=config.identifiers_table.database,
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def apply_migrations(config: IdMinterConfig) -> None:
    """Apply yoyo migrations against the configured database."""
    dsn = (
        f"mysql://{config.rds_client.username}"
        f":{config.rds_client.password}"
        f"@{config.rds_client.primary_host}"
        f":{config.rds_client.port}"
        f"/{config.identifiers_table.database}"
    )
    backend = get_backend(dsn)
    migrations = read_migrations(MIGRATIONS_DIR)

    with backend.lock():
        backend.apply_migrations(backend.to_apply(migrations))

    logger.info("Migrations applied", migrations_dir=MIGRATIONS_DIR)
