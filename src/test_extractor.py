import os

from config import MESH_URL
from conftest import load_fixture
from extractor import lambda_handler
from test_mocks import MockRequest


def test_lambda_handler() -> None:
    content = load_fixture("mesh_example.xml")

    MockRequest.mock_responses(
        [{"method": "GET", "url": MESH_URL, "status_code": 200, "content": content}]
    )

    event = {
        "transformer_type": "mesh_concepts",
        "entity_type": "nodes",
        "stream_destination": "void",
    }

    lambda_handler(event, None)

    assert len(MockRequest.calls) == 1
    request = MockRequest.calls[0]

    # Check we are sending a GET request to the correct endpoint
    assert request["method"] == "GET"
    assert request["url"] == MESH_URL
