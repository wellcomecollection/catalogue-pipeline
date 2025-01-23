import pytest

from test_mocks import MockBoto3Session, MockRequest


@pytest.fixture(autouse=True)
def test(monkeypatch):
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("requests.request", MockRequest.request)
