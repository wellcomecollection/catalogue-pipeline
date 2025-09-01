import os
from collections.abc import Generator
from uuid import uuid1

import pytest
from _pytest.monkeypatch import MonkeyPatch
from pyiceberg.table import Table as IcebergTable

from table_config import get_local_table

from .test_mocks import (
    MockBoto3Resource,
    MockBoto3Session,
    MockElasticsearchClient,
    MockRequest,
    MockSmartOpen,
)

# Add the test directory to the path so we can import from it
HERE = os.path.dirname(os.path.realpath(__file__))


@pytest.fixture(autouse=True)
def common_mocks(monkeypatch: MonkeyPatch) -> Generator[None, None, None]:
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("boto3.resource", MockBoto3Resource)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open)
    monkeypatch.setattr("elasticsearch.Elasticsearch", MockElasticsearchClient)
    monkeypatch.setattr("elasticsearch.helpers.bulk", MockElasticsearchClient.bulk)

    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    yield


@pytest.fixture(autouse=True)
def common_mocks(monkeypatch: MonkeyPatch) -> Generator[None, None, None]:
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("boto3.resource", MockBoto3Resource)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open)
    monkeypatch.setattr("elasticsearch.Elasticsearch", MockElasticsearchClient)
    monkeypatch.setattr("elasticsearch.helpers.bulk", MockElasticsearchClient.bulk)

    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    yield


@pytest.fixture
def temporary_table() -> Generator[IcebergTable, None, None]:
    table_name = str(uuid1())
    table = get_local_table(
        table_name=table_name, namespace="test", db_name="test_catalog"
    )
    yield table
    # For cleanup, we need to get the catalog again
    # Since the table object contains the catalog reference, we can use it
    table.catalog.drop_table(f"test.{table_name}")


@pytest.fixture
def xml_with_one_record() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_one_record.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_two_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_two_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_three_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_three_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def not_xml() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "not_xml.xml")) as xmlfile:
        yield xmlfile
