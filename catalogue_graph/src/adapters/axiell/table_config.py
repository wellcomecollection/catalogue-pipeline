"""Shared table configuration helpers for the Axiell adapter."""

from __future__ import annotations

import contextlib
import os
from typing import Any

import boto3
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.axiell import config
from adapters.utils.iceberg import IcebergTableClient
from adapters.utils.schemata import SCHEMA


def _get_table(
    catalogue_namespace: str,
    table_name: str,
    catalogue_name: str,
    *,
    create_if_not_exists: bool,
    **params: Any,
) -> IcebergTable:
    catalogue = load_catalog(catalogue_name, **params)
    table_fullname = f"{catalogue_namespace}.{table_name}"

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalogue.create_namespace(catalogue_namespace)

        return catalogue.create_table_if_not_exists(
            identifier=table_fullname,
            schema=SCHEMA,
        )

    return catalogue.load_table(table_fullname)


def _rest_api_params(region: str, account_id: str) -> dict[str, str]:
    session = boto3.Session()
    creds = session.get_credentials()
    if creds is None:
        raise RuntimeError("Unable to resolve AWS credentials for accessing S3 Tables")

    frozen = creds.get_frozen_credentials()
    if not frozen.access_key or not frozen.secret_key:
        raise RuntimeError("AWS credentials missing access key or secret key")

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


def _get_rest_api_table(
    *,
    table_name: str,
    namespace: str,
    create_if_not_exists: bool,
) -> IcebergTable:
    session = boto3.Session()
    region = config.AWS_REGION or session.region_name
    account_id = (
        config.AWS_ACCOUNT_ID or session.client("sts").get_caller_identity()["Account"]
    )
    if region is None or account_id is None:
        raise RuntimeError(
            "AWS region/account ID must be configured for REST catalog usage"
        )

    params = _rest_api_params(region, account_id)
    return _get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="axiell_s3tables_catalog",
        create_if_not_exists=create_if_not_exists,
        **params,
    )


def _get_local_table(
    table_name: str = config.LOCAL_TABLE_NAME,
    namespace: str = config.LOCAL_NAMESPACE,
    db_name: str = config.LOCAL_DB_NAME,
) -> IcebergTable:
    package_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(package_dir)
    local_dir = os.path.join(project_root, ".local")
    warehouse_dir = os.path.join(local_dir, "warehouse")

    os.makedirs(warehouse_dir, exist_ok=True)

    return _get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="axiell_local",
        create_if_not_exists=True,
        **{
            "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
            "warehouse": f"file://{warehouse_dir}/",
        },
    )


def get_iceberg_table(*, use_rest_api_table: bool = True) -> IcebergTable:
    if use_rest_api_table:
        return _get_rest_api_table(
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            create_if_not_exists=False,
        )

    return _get_local_table()


__all__ = ["IcebergTableClient", "get_iceberg_table"]
