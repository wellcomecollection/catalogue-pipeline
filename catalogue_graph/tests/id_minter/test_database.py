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
    CONNECT_ATTEMPTS,
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


def add_mock_credentials(username: str, password: str) -> None:
    MockSecretsManagerClient.add_mock_secret(
        SECRET_NAME, json.dumps({"username": username, "password": password})
    )


def access_denied() -> pymysql.err.OperationalError:
    return pymysql.err.OperationalError(
        ACCESS_DENIED_ERROR_CODE, "Access denied for user 'admin'@'10.0.0.1'"
    )


class TestGetCredentials:
    def test_reads_from_secrets_manager_when_secret_name_is_set(self) -> None:
        add_mock_credentials("admin", "rotated_password")

        credentials = get_credentials(config_with_secret())

        assert credentials.username == "admin"
        assert credentials.password == "rotated_password"

    def test_falls_back_to_config_when_no_secret_name(self) -> None:
        credentials = get_credentials(config_with_secret(secret_name=None))

        assert credentials.username == "env_user"
        assert credentials.password == "env_password"
        assert MockSecretsManagerClient.calls == []

    def test_re_reads_the_secret_on_every_call(self) -> None:
        """The secret rotates every 7 days, so a cached value eventually breaks."""
        config = config_with_secret()

        add_mock_credentials("admin", "first_password")
        assert get_credentials(config).password == "first_password"

        add_mock_credentials("admin", "second_password")
        assert get_credentials(config).password == "second_password"


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

    def test_gives_up_after_the_attempt_limit(self) -> None:
        add_mock_credentials("admin", "stale_password")

        with (
            patch("pymysql.connect", side_effect=access_denied()) as connect,
            patch("time.sleep"),
            pytest.raises(pymysql.err.OperationalError),
        ):
            get_connection(config_with_secret())

        assert connect.call_count == CONNECT_ATTEMPTS

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
