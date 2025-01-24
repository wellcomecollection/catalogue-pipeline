import os

import pytest
from _pytest.monkeypatch import MonkeyPatch

from test_mocks import MockBoto3Session, MockRequest


@pytest.fixture(autouse=True)
def test(monkeypatch: MonkeyPatch) -> None:
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("requests.request", MockRequest.request)
    monkeypatch.setattr("requests.get", MockRequest.get)

    monkeypatch.setattr("config.S3_BULK_LOAD_BUCKET_NAME", "bulk_load_test_bucket")
    monkeypatch.setattr(
        "config.GRAPH_QUERIES_SNS_TOPIC_ARN",
        "arn:aws:sns:us-east-1:123456789012:graph_queries_test_topic",
    )


@pytest.fixture(autouse=True)
def run_around_tests():
    MockRequest.reset_mocks()
    yield


def load_fixture(file_name: str) -> bytes:
    with open(f"{os.path.dirname(__file__)}/fixtures/{file_name}", "rb") as f:
        return f.read()
