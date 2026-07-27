"""Tests for credential resolution when opening database connections."""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import patch

import pymysql
import pytest

from id_minter.config import DBConfig, RDSClientConfig
from id_minter.database import (
    ACCESS_DENIED_ERROR_CODE,
    RETRY_STAGES,
    get_connection,
    get_credentials,
)
from tests.mocks import MockSecretsManagerClient

SECRET_NAME = "rds!cluster-a1b2c3"


def config_with_secret(secret_name: str | None = SECRET_NAME) -> DBConfig:
    return DBConfig(
        rds_client=RDSClientConfig(
            primary_host="db.example.com",
            port=3306,
            username="env_user",
            password="env_password",
            secret_name=secret_name,
        ),
        db_name="identifiers",
    )


def add_mock_credentials(
    username: str, password: str, version_stage: str = "AWSCURRENT"
) -> None:
    MockSecretsManagerClient.add_mock_secret(
        SECRET_NAME,
        json.dumps({"username": username, "password": password}),
        version_stage=version_stage,
    )


def read_credentials(config: DBConfig, version_stage: str = "AWSCURRENT") -> Any:
    credentials = get_credentials(config, version_stage)
    assert credentials is not None
    return credentials


def access_denied() -> pymysql.err.OperationalError:
    return pymysql.err.OperationalError(
        ACCESS_DENIED_ERROR_CODE, "Access denied for user 'admin'@'10.0.0.1'"
    )


class TestGetCredentials:
    def test_reads_from_secrets_manager_when_secret_name_is_set(self) -> None:
        add_mock_credentials("admin", "rotated_password")

        credentials = read_credentials(config_with_secret())

        assert credentials.username == "admin"
        assert credentials.password == "rotated_password"

    def test_falls_back_to_config_when_no_secret_name(self) -> None:
        credentials = read_credentials(config_with_secret(secret_name=None))

        assert credentials.username == "env_user"
        assert credentials.password == "env_password"
        assert MockSecretsManagerClient.calls == []

    def test_falls_back_to_config_when_secret_name_is_empty(self) -> None:
        """An env var set but left empty must not be read as a secret name."""
        credentials = read_credentials(config_with_secret(secret_name=""))

        assert credentials.username == "env_user"
        assert MockSecretsManagerClient.calls == []

    def test_returns_none_when_the_version_stage_is_not_staged(self) -> None:
        add_mock_credentials("admin", "current_password")

        assert get_credentials(config_with_secret(), "AWSPENDING") is None

    def test_re_reads_the_secret_on_every_call(self) -> None:
        """The secret rotates every 7 days, so a cached value eventually breaks."""
        config = config_with_secret()

        add_mock_credentials("admin", "first_password")
        assert read_credentials(config).password == "first_password"

        add_mock_credentials("admin", "second_password")
        assert read_credentials(config).password == "second_password"


class TestGetConnection:
    def test_connects_with_credentials_from_secrets_manager(self) -> None:
        add_mock_credentials("admin", "rotated_password")

        with patch("pymysql.connect") as connect:
            get_connection(config_with_secret())

        assert connect.call_args.kwargs["user"] == "admin"
        assert connect.call_args.kwargs["password"] == "rotated_password"

    def test_retries_with_fresh_credentials_when_access_is_denied(self) -> None:
        """Rotation can land between reading the secret and connecting."""
        add_mock_credentials("admin", "stale_password")
        opened_connection: Any = object()

        def connect_or_deny(**kwargs: Any) -> Any:
            if kwargs["password"] == "stale_password":
                add_mock_credentials("admin", "rotated_password")
                raise access_denied()
            return opened_connection

        with (
            patch("pymysql.connect", side_effect=connect_or_deny) as connect,
            patch("time.sleep"),
        ):
            connection = get_connection(config_with_secret())

        assert connection is opened_connection
        assert connect.call_count == 2
        assert connect.call_args.kwargs["password"] == "rotated_password"

    def test_uses_the_pending_version_when_the_current_one_is_denied(self) -> None:
        """Mid-rotation the server already has the password still staged as pending."""
        add_mock_credentials("admin", "superseded_password")
        add_mock_credentials("admin", "pending_password", version_stage="AWSPENDING")
        opened_connection: Any = object()

        def connect_or_deny(**kwargs: Any) -> Any:
            if kwargs["password"] != "pending_password":
                raise access_denied()
            return opened_connection

        with (
            patch("pymysql.connect", side_effect=connect_or_deny) as connect,
            patch("time.sleep") as sleep,
        ):
            connection = get_connection(config_with_secret())

        assert connection is opened_connection
        assert connect.call_count == 2
        assert sleep.call_count == 0  # the pending version is tried immediately

    def test_skips_the_pending_stage_when_nothing_is_staged(self) -> None:
        add_mock_credentials("admin", "stale_password")

        with (
            patch("pymysql.connect", side_effect=access_denied()) as connect,
            patch("time.sleep"),
            pytest.raises(pymysql.err.OperationalError),
        ):
            get_connection(config_with_secret())

        assert connect.call_count == 2

    def test_gives_up_after_every_stage_is_denied(self) -> None:
        add_mock_credentials("admin", "stale_password")
        add_mock_credentials("admin", "also_wrong", version_stage="AWSPENDING")

        with (
            patch("pymysql.connect", side_effect=access_denied()) as connect,
            patch("time.sleep"),
            pytest.raises(pymysql.err.OperationalError),
        ):
            get_connection(config_with_secret())

        assert connect.call_count == len(RETRY_STAGES)

    def test_does_not_retry_other_operational_errors(self) -> None:
        add_mock_credentials("admin", "rotated_password")
        cannot_connect = pymysql.err.OperationalError(2003, "Can't connect to server")

        with (
            patch("pymysql.connect", side_effect=cannot_connect) as connect,
            patch("time.sleep"),
            pytest.raises(pymysql.err.OperationalError),
        ):
            get_connection(config_with_secret())

        assert connect.call_count == 1

    def test_does_not_retry_when_credentials_come_from_config(self) -> None:
        """Without a secret, a retry would just reuse the same bad password."""
        with (
            patch("pymysql.connect", side_effect=access_denied()) as connect,
            patch("time.sleep"),
            pytest.raises(pymysql.err.OperationalError),
        ):
            get_connection(config_with_secret(secret_name=None))

        assert connect.call_count == 1
