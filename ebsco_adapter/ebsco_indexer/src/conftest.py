import pytest
from test_mocks import MockElasticsearchClient, MockBoto3Session


@pytest.fixture(autouse=True)
def test(monkeypatch):
    # Replaces boto3 and Elasticsearch with fake clients
    monkeypatch.setattr("boto3.Session", MockBoto3Session)
    monkeypatch.setattr("elasticsearch.Elasticsearch", MockElasticsearchClient)
    monkeypatch.setattr("main.ES_INDEX_NAME", "test_ebsco_index")
