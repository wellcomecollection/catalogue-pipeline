import pytest
import sys
import os

# Add the app directory to the path so we can import from it
HERE = os.path.dirname(os.path.realpath(__file__))
APP_DIR = os.path.join(os.path.dirname(HERE), "app")
sys.path.insert(0, APP_DIR)

from table_config import get_local_table
from uuid import uuid1


@pytest.fixture
def temporary_table():
    table_name = str(uuid1())
    table = get_local_table(table_name=table_name, namespace="test", db_name="test_catalog")
    yield table
    # For cleanup, we need to get the catalog again
    # Since the table object contains the catalog reference, we can use it
    table.catalog.drop_table(f"test.{table_name}")


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


@pytest.fixture
def not_xml():
    with open(os.path.join(HERE, "data", "not_xml.xml"), "r") as xmlfile:
        yield xmlfile
