import contextlib
import os
from typing import Any

import boto3
from pydantic import BaseModel
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.partitioning import UNPARTITIONED_PARTITION_SPEC, PartitionSpec
from pyiceberg.schema import Schema
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.sorting import SortOrder

from adapters.utils.schemata import ADAPTER_STORE_ICEBERG_SCHEMA


class SharedIcebergTableConfig(BaseModel):
    """Configuration for connecting to an Iceberg table."""

    table_name: str
    namespace: str

    # Only used when creating a new table
    iceberg_schema: Schema = ADAPTER_STORE_ICEBERG_SCHEMA
    partition_spec: PartitionSpec = UNPARTITIONED_PARTITION_SPEC
    sort_order: SortOrder = SortOrder(order_id=0)


class RestApiIcebergTableConfig(SharedIcebergTableConfig):
    s3_tables_bucket: str
    region: str | None = None
    account_id: str | None = None


class LocalIcebergTableConfig(SharedIcebergTableConfig):
    db_name: str = "catalog"


IcebergTableConfig = RestApiIcebergTableConfig | LocalIcebergTableConfig


def get_table(
    config: IcebergTableConfig,
    catalogue_name: str,
    create_if_not_exists: bool,
    **params: Any,
) -> IcebergTable:
    """
    Generic table getter that can be used by any module.

    Args:
        config: IcebergTableConfig to use when creating/connecting to the table
        catalogue_name: Name of the catalog
        create_if_not_exists: Whether to create the table if it doesn't exist
        **params: Additional parameters for loading the catalog

    Returns:
        IcebergTable: The configured table
    """
    catalogue = load_catalog(catalogue_name, **params)
    table_fullname = f"{config.namespace}.{config.table_name}"

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalogue.create_namespace(config.namespace)

        return catalogue.create_table_if_not_exists(
            identifier=table_fullname,
            schema=config.iceberg_schema,
            partition_spec=config.partition_spec,
            sort_order=config.sort_order,
        )

    return catalogue.load_table(table_fullname)


def get_rest_api_catalog_params(
    s3_tables_bucket: str,
    region: str | None = None,
    account_id: str | None = None,
) -> dict[str, str]:
    """
    Get parameters for connecting to an S3 Tables Iceberg REST API catalog.
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

    params = {
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
        "s3.region": region,
    }
    if token:
        params["s3.session-token"] = token

    return params


def get_local_catalog_params(
    db_name: str = "catalog",
) -> dict[str, str]:
    """
    Get parameters for connecting to a local Iceberg catalog.
    """
    # Get the project root directory (parent of app directory)
    app_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(app_dir)
    local_dir = os.path.join(project_root, ".local")

    # For test databases, use a separate warehouse directory
    if db_name.startswith("test"):
        warehouse_dir = os.path.join(local_dir, "test_warehouse")
    else:
        warehouse_dir = os.path.join(local_dir, "warehouse")

    # Ensure directories exist
    os.makedirs(local_dir, exist_ok=True)
    os.makedirs(warehouse_dir, exist_ok=True)

    return {
        "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
        "warehouse": f"file://{warehouse_dir}/",
    }


def get_local_table(
    config: LocalIcebergTableConfig,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    """
    Get a table from the local catalog using the .local directory.

    Args:
        config: LocalIcebergTableConfig object defining the table
        create_if_not_exists: Whether to create the table if it doesn't exist

    Returns:
        IcebergTable: The configured table
    """
    params = get_local_catalog_params(db_name=config.db_name)
    return get_table(
        config=config,
        catalogue_name="local",
        create_if_not_exists=create_if_not_exists,
        **params,
    )


def get_rest_api_table(
    config: RestApiIcebergTableConfig,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    """
    Get a table from an S3 Tables Iceberg REST API catalog.

    Args:
        config: RestApiIcebergTableConfig object defining the table
        create_if_not_exists: Whether to create the table if it doesn't exist

    Returns:
        IcebergTable: The configured table
    """
    params = get_rest_api_catalog_params(
        s3_tables_bucket=config.s3_tables_bucket,
        region=config.region,
        account_id=config.account_id,
    )
    return get_table(
        config=config,
        catalogue_name="s3tablescatalog",
        create_if_not_exists=create_if_not_exists,
        **params,
    )
