import json
from datetime import datetime
from unittest import mock

from .main import lambda_handler
from .test_mocks import MockElasticsearchClient, MockBoto3Session, get_absolute_path


@mock.patch('boto3.Session', return_value=MockBoto3Session())
@mock.patch('elasticsearch.Elasticsearch', return_value=MockElasticsearchClient())
def test_lambda_handler(*args):
    index_event_fixture_file = get_absolute_path("fixtures/index_event.json")
    with open(index_event_fixture_file, "r") as f:
        index_event = json.loads(f.read())
        index_event["invoked_at"] = datetime.utcnow().isoformat()

    lambda_handler(index_event, None)

    delete_event_fixture_file = get_absolute_path("fixtures/delete_event.json")
    with open(delete_event_fixture_file, "r") as f:
        delete_event = json.loads(f.read())
        delete_event["invoked_at"] = datetime.utcnow().isoformat()

    lambda_handler(delete_event, None)
