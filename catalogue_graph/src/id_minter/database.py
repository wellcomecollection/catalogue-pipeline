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
from botocore.exceptions import ClientError
from yoyo import get_backend, read_migrations

from id_minter.config import DBConfig

logger = structlog.get_logger(__name__)

MIGRATIONS_DIR = str(Path(__file__).parent / "migrations")

ACCESS_DENIED_ERROR_CODE = 1045  # MySQL ER_ACCESS_DENIED_ERROR

# Rotation sets the new password on the server before promoting the pending
# secret version, so a denial is retried against the pending version, then
# against the current one once the promotion has had time to land.
RETRY_STAGES = ("AWSCURRENT", "AWSPENDING", "AWSCURRENT")
RETRY_DELAY_SECONDS = 5


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


def _config_credentials(config: DBConfig) -> DBCredentials:
    return DBCredentials(
        username=config.rds_client.username,
        password=config.rds_client.password,
    )


def get_credentials(
    config: DBConfig, version_stage: str = "AWSCURRENT"
) -> DBCredentials | None:
    """Read the credentials from Secrets Manager, or from config if no secret.

    Reading on every call is deliberate: the secret rotates every 7 days, so
    anything cached for the lifetime of a warm Lambda eventually goes stale.
    Returns None if the requested version stage does not exist.
    """
    secret_name = config.rds_client.secret_name
    if not secret_name:
        return _config_credentials(config)

    client = boto3.Session().client("secretsmanager")
    try:
        response = client.get_secret_value(
            SecretId=secret_name, VersionStage=version_stage
        )
    except ClientError as exc:
        if exc.response["Error"]["Code"] != "ResourceNotFoundException":
            raise
        return None

    secret = json.loads(response["SecretString"])
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
    if not config.rds_client.secret_name:
        return connect(_config_credentials(config))

    for attempt, stage in enumerate(RETRY_STAGES, start=1):
        last_attempt = attempt == len(RETRY_STAGES)
        if stage == "AWSCURRENT" and attempt > 1:
            time.sleep(RETRY_DELAY_SECONDS)

        credentials = get_credentials(config, version_stage=stage)
        if credentials is None:  # nothing staged for rotation
            continue

        try:
            return connect(credentials)
        except pymysql.err.OperationalError as exc:
            if last_attempt or not _is_access_denied(exc):
                raise
            logger.warning("Database access denied, retrying", version_stage=stage)

    raise RuntimeError(f"No usable credentials in {config.rds_client.secret_name}")


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
