"""
Shared table configuration for the EBSCO adapter.
"""
import os
from pyiceberg.catalog import load_catalog
from pyiceberg.table import Table as IcebergTable
from schemata import SCHEMA


def get_table(
    catalogue_name, catalogue_uri, catalogue_warehouse, catalogue_namespace, table_name
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
        uri=catalogue_uri,
        warehouse=catalogue_warehouse,
    )
    catalogue.create_namespace_if_not_exists(catalogue_namespace)
    table_fullname = f"{catalogue_namespace}.{table_name}"
    table = catalogue.create_table_if_not_exists(
        identifier=table_fullname, schema=SCHEMA
    )
    return table


def get_local_table(table_name="mytable", namespace="default", db_name="catalog"):
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
        catalogue_name="local",
        catalogue_uri=f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
        catalogue_warehouse=f"file://{warehouse_dir}/",
        catalogue_namespace=namespace,
        table_name=table_name,
    )
