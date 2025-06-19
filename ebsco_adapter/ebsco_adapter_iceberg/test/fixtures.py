import pytest
from pyiceberg.catalog import load_catalog

from schemata import SCHEMA
from uuid import uuid1


def setup_test_db(table_name):
    catalog = load_catalog("local", uri="sqlite:////tmp/warehouse/test_catalog.db", warehouse="file:///tmp/warehouse/")
    table_fullname = f'test.{table_name}'
    catalog.create_namespace_if_not_exists("test")
    table = catalog.create_table_if_not_exists(
        identifier=table_fullname,
        schema=SCHEMA
    )
    return catalog, table


@pytest.fixture
def temporary_table():
    table_name = str(uuid1())
    catalogue, table = setup_test_db(table_name)
    yield table
    catalogue.drop_table(f'test.{table_name}')
