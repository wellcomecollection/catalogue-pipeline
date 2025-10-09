import json

from indexer import lambda_handler
from tests.mocks import MockRequest


def test_lambda_handler() -> None:
    MockRequest.mock_responses(
        [
            {
                "method": "POST",
                "url": "https://test-host.com:8182/openCypher",
                "status_code": 200,
                "json_data": {"results": {"foo": "bar"}},
                "content_bytes": None,
                "params": None,
            }
        ]
    )

    event = {"Records": [{"body": json.dumps({"Message": "SOME_QUERY"})}]}

    lambda_handler(event, None)

    assert len(MockRequest.calls) == 1
    request = MockRequest.calls[0]

    # Check we are sending a POST request to the correct endpoint
    assert request["method"] == "POST"
    assert request["url"] == "https://test-host.com:8182/openCypher"

    # Check we are sending the correct data
    assert request["data"] == json.dumps({"query": "SOME_QUERY"})

    # Check we are building a SigV4Auth header
    assert "Authorization" in request["headers"]
    assert "AWS4-HMAC-SHA256" in request["headers"]["Authorization"]
