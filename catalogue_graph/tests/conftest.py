from collections.abc import Generator
from typing import Any

import pytest
from _pytest.monkeypatch import MonkeyPatch

from tests.mocks import (
    MockBoto3Resource,
    MockBoto3Session,
    MockCloudwatchClient,
    MockElasticsearchClient,
    MockRequest,
    MockS3Client,
    MockSecretsManagerClient,
    MockSmartOpen,
    MockSNSClient,
    MockStepFunctionsClient,
    mock_boto3_client,
)
from utils.aws import get_secret


def pytest_addoption(parser: pytest.Parser) -> None:
    parser.addoption("--skip-db", action="store_true", help="Skip database tests")


def pytest_collection_modifyitems(
    config: pytest.Config, items: list[pytest.Item]
) -> None:
    if config.getoption("--skip-db"):
        skip = pytest.mark.skip(reason="--skip-db flag set")
        for item in items:
            if "database" in item.keywords:
                item.add_marker(skip)


@pytest.fixture(autouse=True)
def test(monkeypatch: MonkeyPatch) -> Generator[Any, Any, Any]:
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("boto3.resource", MockBoto3Resource)
    monkeypatch.setattr("boto3.client", mock_boto3_client)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open)
    monkeypatch.setattr("elasticsearch.Elasticsearch", MockElasticsearchClient)
    monkeypatch.setattr("elasticsearch.helpers.bulk", MockElasticsearchClient.bulk)

    monkeypatch.setattr("config.ES_SOURCE_PARALLELISM", 1)
    monkeypatch.setattr("config.ES_SOURCE_SLICE_COUNT", 1)

    MockRequest.reset_mocks()
    MockSecretsManagerClient.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockSNSClient.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    MockCloudwatchClient.reset_mocks()
    MockS3Client.reset_mocks()
    MockStepFunctionsClient.reset_mocks()
    get_secret.cache_clear()  # Clear cached SecretsManager secrets after each test
    yield
    # Run any cleanup code here
