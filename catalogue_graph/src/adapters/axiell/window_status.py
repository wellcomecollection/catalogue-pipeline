"""Window status table helpers for the Axiell adapter."""

from __future__ import annotations

import contextlib
import os
from typing import Any

import boto3
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    IcebergWindowStore,
)

from . import config


def _rest_catalog_params(region: str, account_id: str) -> dict[str, str]:
    session = boto3.Session()
    creds = session.get_credentials()
    if creds is None:
        raise RuntimeError("Unable to resolve AWS credentials for S3 Tables access")

    frozen = creds.get_frozen_credentials()
    if not frozen.access_key or not frozen.secret_key:
        raise RuntimeError("Resolved AWS credentials are missing access or secret keys")

    params = {
        "type": "rest",
        "warehouse": f"arn:aws:s3tables:{region}:{account_id}:bucket/{config.S3_TABLES_BUCKET}",
        "uri": f"https://s3tables.{region}.amazonaws.com/iceberg",
        "rest.sigv4-enabled": "true",
        "rest.signing-name": "s3tables",
        "rest.signing-region": region,
        "s3.access-key-id": frozen.access_key,
        "s3.secret-access-key": frozen.secret_key,
        "s3.region": region,
    }
    if frozen.token:
        params["s3.session-token"] = frozen.token
    return params


def _local_catalog_params(db_name: str) -> dict[str, str]:
    package_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(package_dir)
    local_dir = os.path.join(project_root, ".local")
    warehouse_dir = os.path.join(local_dir, "warehouse")

    os.makedirs(warehouse_dir, exist_ok=True)

    return {
        "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
        "warehouse": f"file://{warehouse_dir}/",
    }


def _load_rest_window_status_table(*, create_if_not_exists: bool) -> IcebergTable:
    session = boto3.Session()
    region = config.AWS_REGION or session.region_name
    account_id = (
        config.AWS_ACCOUNT_ID or session.client("sts").get_caller_identity()["Account"]
    )
    if region is None or account_id is None:
        raise RuntimeError(
            "AWS region/account must be configured to load window status table"
        )

    params = _rest_catalog_params(region, account_id)
    catalog = load_catalog(config.WINDOW_STATUS_CATALOG_NAME, **params)
    identifier = f"{config.WINDOW_STATUS_NAMESPACE}.{config.WINDOW_STATUS_TABLE}"

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalog.create_namespace(config.WINDOW_STATUS_NAMESPACE)
        return catalog.create_table_if_not_exists(
            identifier=identifier,
            schema=WINDOW_STATUS_SCHEMA,
            partition_spec=WINDOW_STATUS_PARTITION_SPEC,
        )

    return catalog.load_table(identifier)


def _load_local_window_status_table(*, create_if_not_exists: bool) -> IcebergTable:
    params: dict[str, Any] = _local_catalog_params(config.LOCAL_WINDOW_STATUS_DB_NAME)
    catalog = load_catalog(config.LOCAL_WINDOW_STATUS_CATALOG_NAME, **params)
    identifier = (
        f"{config.LOCAL_WINDOW_STATUS_NAMESPACE}.{config.LOCAL_WINDOW_STATUS_TABLE}"
    )

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalog.create_namespace(config.LOCAL_WINDOW_STATUS_NAMESPACE)
        return catalog.create_table_if_not_exists(
            identifier=identifier,
            schema=WINDOW_STATUS_SCHEMA,
            partition_spec=WINDOW_STATUS_PARTITION_SPEC,
        )

    return catalog.load_table(identifier)


def load_window_status_table(
    *,
    create_if_not_exists: bool = True,
    use_rest_api_table: bool = True,
) -> IcebergTable:
    """Load or create the Iceberg table backing the window status store."""

    if use_rest_api_table:
        return _load_rest_window_status_table(create_if_not_exists=create_if_not_exists)

    return _load_local_window_status_table(create_if_not_exists=create_if_not_exists)


def build_window_store(*, use_rest_api_table: bool = True) -> IcebergWindowStore:
    table = load_window_status_table(use_rest_api_table=use_rest_api_table)
    return IcebergWindowStore(table)
