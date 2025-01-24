from typing import Any, Generator

import pytest
from _pytest.monkeypatch import MonkeyPatch

from test_mocks import MockBoto3Session, MockRequest, MockSmartOpen, MockSNSClient


@pytest.fixture(autouse=True)
def test(monkeypatch: MonkeyPatch) -> Generator[Any, Any, Any]:
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open)

    monkeypatch.setattr("config.S3_BULK_LOAD_BUCKET_NAME", "bulk_load_test_bucket")
    monkeypatch.setattr(
        "config.GRAPH_QUERIES_SNS_TOPIC_ARN",
        "arn:aws:sns:us-east-1:123456789012:graph_queries_test_topic",
    )

    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockSNSClient.reset_mocks()
    yield
    # Run any cleanup code here
