"""Database access layer for the ID Minter.

Provides connection management and yoyo migration support for the
identifiers database — following the schema defined in RFC 083.
"""

from __future__ import annotations

from collections.abc import Sequence
from pathlib import Path
from typing import Any, Protocol, cast
from urllib.parse import quote

import pymysql
import pymysql.cursors
import structlog
from yoyo import get_backend, read_migrations

from id_minter.config import DBConfig

logger = structlog.get_logger(__name__)

MIGRATIONS_DIR = str(Path(__file__).parent / "migrations")


class DBCursor(Protocol):
    def execute(self, q: str, args: Sequence[Any] | None = ...) -> None: ...

    def fetchone(self) -> dict[str, Any]: ...

    def fetchall(self) -> list[dict[str, Any]]: ...

    def executemany(self, q: str, args: list[tuple]) -> None: ...


class DBConnection[T: DBCursor](Protocol):
    def cursor(self) -> T: ...

    def commit(self) -> None: ...

    def rollback(self) -> None: ...

    def close(self) -> None: ...


def get_connection(config: DBConfig, *, local_infile: bool = False) -> DBConnection:
    """Open a new pymysql connection using the ID Minter config."""
    return cast(
        DBConnection,
        pymysql.connect(
            host=config.rds_client.primary_host,
            port=config.rds_client.port,
            user=config.rds_client.username,
            password=config.rds_client.password,
            database=config.db_name,
            cursorclass=pymysql.cursors.DictCursor,
            autocommit=False,
            local_infile=local_infile,
        ),
    )


def apply_migrations(config: DBConfig) -> None:
    """Apply yoyo migrations against the configured database."""
    dsn = (
        f"mysql://{quote(config.rds_client.username, safe='')}"
        f":{quote(config.rds_client.password, safe='')}"
        f"@{config.rds_client.primary_host}"
        f":{config.rds_client.port}"
        f"/{config.db_name}"
    )
    backend = get_backend(dsn)
    migrations = read_migrations(MIGRATIONS_DIR)

    with backend.lock():
        backend.apply_migrations(backend.to_apply(migrations))

    logger.info("Migrations applied", migrations_dir=MIGRATIONS_DIR)
