"""Database access layer for the ID Minter.

Provides connection management and yoyo migration support for the
identifiers database — following the schema defined in RFC 083.
"""

from __future__ import annotations

import json
import time
from collections.abc import Callable, Sequence
from pathlib import Path
from typing import Any, NamedTuple, Protocol, cast
from urllib.parse import quote

import boto3
import pymysql
import pymysql.cursors
import structlog
from yoyo import get_backend, read_migrations

from id_minter.config import DBConfig

logger = structlog.get_logger(__name__)

MIGRATIONS_DIR = str(Path(__file__).parent / "migrations")

ACCESS_DENIED_ERROR_CODE = 1045  # MySQL ER_ACCESS_DENIED_ERROR

# Rotation changes the server password before the new secret version becomes
# current, so a connect in that window is denied even with a fresh read.
CONNECT_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 2


class DBCursor(Protocol):
    def execute(self, q: str, args: Sequence[Any] | None = ...) -> int: ...

    def fetchone(self) -> dict[str, Any]: ...

    def fetchall(self) -> list[dict[str, Any]]: ...

    def executemany(self, q: str, args: list[tuple]) -> None: ...


class DBConnection[T: DBCursor](Protocol):
    def cursor(self) -> T: ...

    def commit(self) -> None: ...

    def rollback(self) -> None: ...

    def close(self) -> None: ...


class DBCredentials(NamedTuple):
    username: str
    password: str


def get_credentials(config: DBConfig) -> DBCredentials:
    """Read the credentials from Secrets Manager, or from config if no secret.

    Reading on every call is deliberate: the secret rotates every 7 days, so
    anything cached for the lifetime of a warm Lambda eventually goes stale.
    """
    secret_name = config.rds_client.secret_name
    if secret_name is None:
        return DBCredentials(
            username=config.rds_client.username,
            password=config.rds_client.password,
        )

    client = boto3.Session().client("secretsmanager")
    secret = json.loads(client.get_secret_value(SecretId=secret_name)["SecretString"])
    return DBCredentials(username=secret["username"], password=secret["password"])


def _is_access_denied(exc: pymysql.err.OperationalError) -> bool:
    return bool(exc.args) and exc.args[0] == ACCESS_DENIED_ERROR_CODE


def _connect_with_retry[T](
    config: DBConfig, connect: Callable[[DBCredentials], T]
) -> T:
    """Run ``connect`` with fresh credentials, retrying if access is denied.

    Retrying only helps when the credentials can change between attempts, so it
    is skipped when they come from config rather than Secrets Manager.
    """
    attempts = CONNECT_ATTEMPTS if config.rds_client.secret_name else 1

    for attempt in range(1, attempts + 1):
        try:
            return connect(get_credentials(config))
        except pymysql.err.OperationalError as exc:
            if attempt == attempts or not _is_access_denied(exc):
                raise
            delay = RETRY_DELAY_SECONDS * attempt
            logger.warning(
                "Database access denied, re-reading credentials before retrying",
                attempt=attempt,
                attempts=attempts,
                retry_in_seconds=delay,
            )
            time.sleep(delay)

    raise AssertionError("unreachable")


def get_connection(
    config: DBConfig, *, local_infile: bool = False
) -> DBConnection[DBCursor]:
    """Open a new pymysql connection using the ID Minter config."""

    def connect(credentials: DBCredentials) -> DBConnection[DBCursor]:
        return cast(
            DBConnection[DBCursor],
            pymysql.connect(
                host=config.rds_client.primary_host,
                port=config.rds_client.port,
                user=credentials.username,
                password=credentials.password,
                database=config.db_name,
                cursorclass=pymysql.cursors.DictCursor,
                autocommit=False,
                local_infile=local_infile,
            ),
        )

    return _connect_with_retry(config, connect)


def apply_migrations(config: DBConfig) -> None:
    """Apply yoyo migrations against the configured database."""

    def connect(credentials: DBCredentials) -> Any:
        dsn = (
            f"mysql://{quote(credentials.username, safe='')}"
            f":{quote(credentials.password, safe='')}"
            f"@{config.rds_client.primary_host}"
            f":{config.rds_client.port}"
            f"/{config.db_name}"
        )
        return get_backend(dsn)

    backend = _connect_with_retry(config, connect)
    migrations = read_migrations(MIGRATIONS_DIR)

    with backend.lock():
        backend.apply_migrations(backend.to_apply(migrations))

    logger.info("Migrations applied", migrations_dir=MIGRATIONS_DIR)
