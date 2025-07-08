import pytest
from pyiceberg.catalog import load_catalog

from schemata import SCHEMA
from uuid import uuid1
import os

HERE = os.path.dirname(os.path.realpath(__file__))


def setup_test_db(table_name):
    catalog = load_catalog(
        "local",
        uri="sqlite:////tmp/warehouse/test_catalog.db",
        warehouse="file:///tmp/warehouse/",
    )
    table_fullname = f"test.{table_name}"
    catalog.create_namespace_if_not_exists("test")
    table = catalog.create_table_if_not_exists(identifier=table_fullname, schema=SCHEMA)
    return catalog, table


@pytest.fixture
def temporary_table():
    table_name = str(uuid1())
    catalogue, table = setup_test_db(table_name)
    yield table
    catalogue.drop_table(f"test.{table_name}")


@pytest.fixture
def xml_with_one_record():
    with open(os.path.join(HERE, "data", "with_one_record.xml"), "r") as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_two_records():
    with open(os.path.join(HERE, "data", "with_two_records.xml"), "r") as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_three_records():
    with open(os.path.join(HERE, "data", "with_three_records.xml"), "r") as xmlfile:
        yield xmlfile
