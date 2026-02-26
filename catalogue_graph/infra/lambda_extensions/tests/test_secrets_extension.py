import json
import os
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from secrets_extension import (
    create_dotenv,
    fetch_secret,
    resolve_secret,
)


class TestResolveSecret:
    def test_plain_secret(self):
        name, json_key = resolve_secret("secret:rds/my-cluster/endpoint")
        assert name == "rds/my-cluster/endpoint"
        assert json_key is None

    def test_json_key_secret(self):
        name, json_key = resolve_secret("secret:rds!cluster-abc123:password")
        assert name == "rds!cluster-abc123"
        assert json_key == "password"

    def test_json_key_with_colons(self):
        name, json_key = resolve_secret("secret:my-secret:key:with:colons")
        assert name == "my-secret"
        assert json_key == "key:with:colons"


class TestFetchSecret:
    def test_plain_string(self):
        client = MagicMock()
        client.get_secret_value.return_value = {"SecretString": "db.example.com"}

        result = fetch_secret(client, "rds/my-cluster/endpoint", None)

        assert result == "db.example.com"
        client.get_secret_value.assert_called_once_with(
            SecretId="rds/my-cluster/endpoint"
        )

    def test_json_key_extraction(self):
        client = MagicMock()
        client.get_secret_value.return_value = {
            "SecretString": json.dumps({"username": "admin", "password": "s3cret!"})
        }

        assert fetch_secret(client, "rds!cluster-abc", "password") == "s3cret!"

    def test_json_key_username(self):
        client = MagicMock()
        client.get_secret_value.return_value = {
            "SecretString": json.dumps({"username": "admin", "password": "s3cret!"})
        }

        assert fetch_secret(client, "rds!cluster-abc", "username") == "admin"

    def test_missing_json_key_raises(self):
        client = MagicMock()
        client.get_secret_value.return_value = {
            "SecretString": json.dumps({"username": "admin"})
        }

        with pytest.raises(KeyError):
            fetch_secret(client, "rds!cluster-abc", "password")

    def test_numeric_json_value_returned_as_string(self):
        client = MagicMock()
        client.get_secret_value.return_value = {
            "SecretString": json.dumps({"port": 3306})
        }

        assert fetch_secret(client, "rds!cluster-abc", "port") == "3306"


class TestCreateDotenv:
    @pytest.fixture()
    def dotenv_path(self, tmp_path):
        return str(tmp_path / ".env")

    def test_writes_only_secret_vars(self, dotenv_path):
        mock_client = MagicMock()
        mock_client.get_secret_value.return_value = {"SecretString": "db.example.com"}

        env = {
            "RDS_HOST": "secret:rds/my-cluster/endpoint",
            "PLAIN_VAR": "not-a-secret",
        }

        with (
            patch.dict(os.environ, env, clear=True),
            patch("secrets_extension.boto3") as mock_boto3,
            patch("secrets_extension.DOTENV_FILE", dotenv_path),
        ):
            mock_boto3.client.return_value = mock_client
            create_dotenv()

        content = Path(dotenv_path).read_text()
        assert 'RDS_HOST="db.example.com"' in content
        assert "PLAIN_VAR" not in content

    def test_resolves_json_secrets(self, dotenv_path):
        mock_client = MagicMock()
        mock_client.get_secret_value.return_value = {
            "SecretString": json.dumps({"username": "admin", "password": "s3cret!"})
        }

        env = {
            "RDS_USERNAME": "secret:rds!cluster-abc:username",
            "RDS_PASSWORD": "secret:rds!cluster-abc:password",
        }

        with (
            patch.dict(os.environ, env, clear=True),
            patch("secrets_extension.boto3") as mock_boto3,
            patch("secrets_extension.DOTENV_FILE", dotenv_path),
        ):
            mock_boto3.client.return_value = mock_client
            create_dotenv()

        content = Path(dotenv_path).read_text()
        assert 'RDS_USERNAME="admin"' in content
        assert 'RDS_PASSWORD="s3cret!"' in content

    def test_escapes_special_characters(self, dotenv_path):
        mock_client = MagicMock()
        mock_client.get_secret_value.return_value = {
            "SecretString": 'pass"word\\with\\special'
        }

        env = {"DB_PASS": "secret:my-secret"}

        with (
            patch.dict(os.environ, env, clear=True),
            patch("secrets_extension.boto3") as mock_boto3,
            patch("secrets_extension.DOTENV_FILE", dotenv_path),
        ):
            mock_boto3.client.return_value = mock_client
            create_dotenv()

        content = Path(dotenv_path).read_text()
        assert 'DB_PASS="pass\\"word\\\\with\\\\special"' in content

    def test_exits_on_secret_fetch_failure(self, dotenv_path):
        mock_client = MagicMock()
        mock_client.get_secret_value.side_effect = Exception("Access denied")

        env = {"DB_PASS": "secret:my-secret"}

        with (
            patch.dict(os.environ, env, clear=True),
            patch("secrets_extension.boto3") as mock_boto3,
            patch("secrets_extension.DOTENV_FILE", dotenv_path),
            pytest.raises(SystemExit) as exc_info,
        ):
            mock_boto3.client.return_value = mock_client
            create_dotenv()

        assert exc_info.value.code == 1

    def test_empty_env_writes_empty_file(self, dotenv_path):
        with (
            patch.dict(os.environ, {}, clear=True),
            patch("secrets_extension.boto3") as mock_boto3,
            patch("secrets_extension.DOTENV_FILE", dotenv_path),
        ):
            create_dotenv()

        content = Path(dotenv_path).read_text()
        assert content == ""
        mock_boto3.client.return_value.get_secret_value.assert_not_called()
