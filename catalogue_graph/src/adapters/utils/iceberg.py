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

from adapters.utils.schemata import SCHEMA


class IcebergTableConfig(BaseModel):
    """Configuration for connecting to an Iceberg table."""

    # Common configuration
    table_name: str
    namespace: str
    use_rest_api_table: bool = True
    create_if_not_exists: bool = True

    # REST API configuration (S3 Tables)
    s3_tables_bucket: str | None = None
    region: str | None = None
    account_id: str | None = None

    # Local configuration
    db_name: str | None = None


def get_table(
    catalogue_namespace: str,
    table_name: str,
    catalogue_name: str,
    create_if_not_exists: bool,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    **params: Any,
) -> IcebergTable:
    """
    Generic table getter that can be used by any module.

    Args:
        catalogue_name: Name of the catalog
        catalogue_namespace: Namespace for the table
        table_name: Name of the table
        create_if_not_exists: Whether to create the table if it doesn't exist
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        **params: Additional parameters for loading the catalog

    Returns:
        IcebergTable: The configured table
    """
    catalogue = load_catalog(
        catalogue_name,
        **params,
    )
    table_fullname = f"{catalogue_namespace}.{table_name}"

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalogue.create_namespace(catalogue_namespace)

        return catalogue.create_table_if_not_exists(
            identifier=table_fullname,
            schema=schema,
            partition_spec=partition_spec or UNPARTITIONED_PARTITION_SPEC,
        )

    return catalogue.load_table(table_fullname)


def get_rest_api_table(
    s3_tables_bucket: str,
    table_name: str,
    namespace: str,
    create_if_not_exists: bool = False,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    *,
    region: str | None = None,
    account_id: str | None = None,
) -> IcebergTable:
    """
    Get a table from an S3 Tables Iceberg REST API catalog.

    Args:
        s3_tables_bucket: S3 bucket for the S3 Table
        table_name: Name of the table
        namespace: Namespace for the table
        create_if_not_exists: Whether to create the table if it doesn't exist
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        region: AWS region where the S3 Table is located
        account_id: AWS account ID

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

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="s3tablescatalog",
        create_if_not_exists=create_if_not_exists,
        schema=schema,
        partition_spec=partition_spec,
        **params,
    )


def get_local_table(
    table_name: str = "mytable",
    namespace: str = "default",
    db_name: str = "catalog",
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    """
    Get a table from the local catalog using the .local directory.

    Args:
        table_name: Name of the table (defaults to "mytable")
        namespace: Namespace for the table (defaults to "default")
        db_name: Database name (defaults to "catalog", use "test_catalog" for tests)
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        create_if_not_exists: Whether to create the table if it doesn't exist

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
        warehouse_dir = os.path.join(local_dir, "warehouse")

    # Ensure directories exist
    os.makedirs(local_dir, exist_ok=True)
    os.makedirs(warehouse_dir, exist_ok=True)

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=create_if_not_exists,
        schema=schema,
        partition_spec=partition_spec,
        **{
            "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
            "warehouse": f"file://{warehouse_dir}/",
        },
    )


def get_iceberg_table(
    config: IcebergTableConfig,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
) -> IcebergTable:
    """
    Get an Iceberg table based on the provided configuration.

    Args:
        config: Configuration object containing table details.
        schema: Schema to use when creating the table.
        partition_spec: Partition spec to use when creating the table.

    Returns:
        IcebergTable: The configured table.
    """
    if config.use_rest_api_table:
        if not config.s3_tables_bucket:
            raise ValueError(
                "s3_tables_bucket must be provided when use_rest_api_table is True"
            )
        return get_rest_api_table(
            s3_tables_bucket=config.s3_tables_bucket,
            table_name=config.table_name,
            namespace=config.namespace,
            create_if_not_exists=config.create_if_not_exists,
            schema=schema,
            partition_spec=partition_spec,
            region=config.region,
            account_id=config.account_id,
        )

    return get_local_table(
        table_name=config.table_name,
        namespace=config.namespace,
        db_name=config.db_name or "catalog",
        schema=schema,
        partition_spec=partition_spec,
        create_if_not_exists=config.create_if_not_exists,
    )
