"""
Shared table configuration for the EBSCO adapter.
"""

import contextlib
import os
from typing import Any

import boto3
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import (
    NamespaceAlreadyExistsError,
)
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco.schemata import SCHEMA


def get_table(
    catalogue_namespace: str,
    table_name: str,
    catalogue_name: str,
    create_if_not_exists: bool,
    **params: Any,
) -> IcebergTable:
    """
    Generic table getter that can be used by any module.

    Args:
        catalogue_name: Name of the catalog
        catalogue_uri: URI for the catalog database
        catalogue_warehouse: Warehouse directory path
        catalogue_namespace: Namespace for the table
        table_name: Name of the table

    Returns:
        IcebergTable: The configured table
    """

    catalogue = load_catalog(
        catalogue_name,
        **params,
    )
    table_fullname = f"{catalogue_namespace}.{table_name}"

    print(f"Using {table_fullname} in {catalogue_namespace} catalog")

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalogue.create_namespace(catalogue_namespace)

        return catalogue.create_table_if_not_exists(
            identifier=table_fullname, schema=SCHEMA
        )

    return catalogue.load_table(table_fullname)


def get_rest_api_table(
    s3_tables_bucket: str,
    table_name: str,
    namespace: str,
    create_if_not_exists: bool = False,
    *,
    region: str | None = None,
    account_id: str | None = None,
) -> IcebergTable:
    """
    Get a table from an S3 Tables Iceberg REST API catalog.

    Args:
        region: AWS region where the S3 Table is located
        account_id: AWS account ID
        s3_tables_bucket: S3 bucket for the S3 Table
        table_name: Name of the table
        namespace: Namespace for the table
    Returns:
        IcebergTable: The configured table
    """

    session = boto3.Session()
    region = region or session.region_name
    account_id = account_id or session.client("sts").get_caller_identity()["Account"]

    # Use credentials resolved for this session (env vars, shared config, SSO, etc.)
    creds = session.get_credentials()
    if creds is None:
        raise RuntimeError(
            "AWS credentials could not be obtained from the boto3 session."
        )

    frozen = creds.get_frozen_credentials()
    access_key = frozen.access_key
    secret_key = frozen.secret_key
    token = frozen.token
    if not access_key or not secret_key:
        raise RuntimeError(
            "Incomplete AWS credentials: access key or secret key is missing."
        )

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="s3tablescatalog",
        create_if_not_exists=create_if_not_exists,
        **{
            "type": "rest",
            "warehouse": f"arn:aws:s3tables:{region}:{account_id}:bucket/{s3_tables_bucket}",
            "uri": f"https://s3tables.{region}.amazonaws.com/iceberg",
            "rest.sigv4-enabled": "true",
            "rest.signing-name": "s3tables",
            "rest.signing-region": region,
            # As we are using the Iceberg REST API exposed by S3 Tables,
            # instead of the Glue catalog, we need S3 credentials here.
            # Using the Glue catalog requires provisioning more complex
            # Lake Formation permissions, which are not required.
            "s3.access-key-id": access_key,
            "s3.secret-access-key": secret_key,
            "s3.session-token": token,
            "s3.region": region,
        },
    )


def get_local_table(
    table_name: str = "mytable", namespace: str = "default", db_name: str = "catalog"
) -> IcebergTable:
    """
    Get a table from the local catalog using the .local directory.

    Args:
        table_name: Name of the table (defaults to "mytable")
        namespace: Namespace for the table (defaults to "default")
        db_name: Database name (defaults to "catalog", use "test_catalog" for tests)

    Returns:
        IcebergTable: The configured table
    """
    # Get the project root directory (parent of app directory)
    app_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(app_dir)
    local_dir = os.path.join(project_root, ".local")

    # For test databases, use a separate warehouse directory
    if db_name.startswith("test"):
        warehouse_dir = os.path.join(local_dir, "test_warehouse")
    else:
        warehouse_dir = local_dir

    # Ensure directories exist
    os.makedirs(local_dir, exist_ok=True)
    os.makedirs(warehouse_dir, exist_ok=True)

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=True,
        **{
            "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
            "warehouse": f"file://{warehouse_dir}/",
        },
    )
