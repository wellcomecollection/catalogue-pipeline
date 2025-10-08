from collections.abc import Generator
from typing import Any

import pytest
from _pytest.monkeypatch import MonkeyPatch
from test_mocks import (
    MockBoto3Resource,
    MockBoto3Session,
    MockCloudwatchClient,
    MockElasticsearchClient,
    MockRequest,
    MockSmartOpen,
    MockSNSClient,
)


@pytest.fixture(autouse=True)
def test(monkeypatch: MonkeyPatch) -> Generator[Any, Any, Any]:
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("boto3.resource", MockBoto3Resource)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open)
    monkeypatch.setattr("elasticsearch.Elasticsearch", MockElasticsearchClient)
    monkeypatch.setattr("elasticsearch.helpers.bulk", MockElasticsearchClient.bulk)

    monkeypatch.setattr(
        "config.GRAPH_QUERIES_SNS_TOPIC_ARN",
        "arn:aws:sns:us-east-1:123456789012:graph_queries_test_topic",
    )

    monkeypatch.setattr("config.ES_SOURCE_PARALLELISM", 1)
    monkeypatch.setattr("config.ES_SOURCE_SLICE_COUNT", 1)

    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockSNSClient.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    MockCloudwatchClient.reset_mocks()
    yield
    # Run any cleanup code here
