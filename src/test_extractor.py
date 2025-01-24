from typing import Any, Generator

import pytest
from typing_extensions import get_args

from config import LOC_SUBJECT_HEADINGS_URL, MESH_URL
from extractor import LambdaEvent, lambda_handler
from test_mocks import MockRequest, MockResponseInput
from test_utils import load_fixture
from transformers.base_transformer import EntityType, StreamDestination
from transformers.create_transformer import TransformerType


def mock_requests_lookup_table(
    destination: StreamDestination,
    transformer_type: TransformerType,
) -> Any:
    mock_mesh_retrieval = {
        "method": "GET",
        "url": MESH_URL,
        "status_code": 200,
        "content_bytes": load_fixture("mesh_example.xml"),
        "json_data": None,
    }

    mock_loc_subjects_retrieval = {
        "method": "GET",
        "url": LOC_SUBJECT_HEADINGS_URL,
        "status_code": 200,
        "content_bytes": load_fixture("loc_subjects_example.jsonld"),
        "json_data": None,
    }

    mock_graph_post = {
        "method": "POST",
        "url": "https://test-host.com:8182/openCypher",
        "status_code": 200,
        "content_bytes": None,
        "json_data": {"results": {}},
    }

    if transformer_type == "mesh_concepts":
        if destination == "s3" or destination == "void" or destination == "sns":
            return [mock_mesh_retrieval]
        elif destination == "graph":
            return [mock_mesh_retrieval, mock_graph_post]
    elif transformer_type == "loc_concepts":
        if destination == "s3" or destination == "void" or destination == "sns":
            return [mock_loc_subjects_retrieval]
        elif destination == "graph":
            return [mock_loc_subjects_retrieval, mock_graph_post]

    raise ValueError(
        f"Unsupported destination: {destination}, transformer_type: {transformer_type}"
    )


def build_test_matrix() -> Generator[tuple[LambdaEvent, list[MockResponseInput]], Any]:
    transformer_types = get_args(TransformerType)
    entity_types = get_args(EntityType)
    stream_destinations = get_args(StreamDestination)

    transformer_types_to_test = [
        transformer_type
        for transformer_type in transformer_types
        if transformer_type in ["mesh_concepts", "loc_concepts"]
    ]

    stream_destination_to_test = [
        stream_destination
        for stream_destination in stream_destinations
        if stream_destination in ["graph", "s3", "void", "sns"]
    ]

    entity_type_to_test = [
        entity_type for entity_type in entity_types if entity_type in ["nodes", "edges"]
    ]

    for transformer_type in transformer_types_to_test:
        for entity_type in entity_type_to_test:
            for stream_destination in stream_destination_to_test:
                yield (
                    {
                        "transformer_type": transformer_type,
                        "entity_type": entity_type,
                        "stream_destination": stream_destination,
                        "sample_size": 1,
                    },
                    mock_requests_lookup_table(stream_destination, transformer_type),
                )


@pytest.mark.parametrize(
    "lambda_event, mock_responses",
    build_test_matrix(),
)
def test_lambda_handler(
    lambda_event: LambdaEvent,
    mock_responses: list[MockResponseInput],
) -> None:

    MockRequest.mock_responses(mock_responses)
    lambda_handler(lambda_event, None)

    transformer_type = lambda_event["transformer_type"]
    entity_type = lambda_event["entity_type"]
    destination = lambda_event["stream_destination"]

    if transformer_type == "mesh_concepts":
        if destination == "void" or destination == "s3" or destination == "sns":
            assert len(MockRequest.calls) == 1
            request = MockRequest.calls[0]

            assert request["method"] == "GET"
            assert request["url"] == MESH_URL

        elif destination == "graph":
            assert len(MockRequest.calls) == 2
            mesh_request = MockRequest.calls[0]

            assert mesh_request["method"] == "GET"
            assert mesh_request["url"] == MESH_URL

            graph_request = MockRequest.calls[1]

            assert graph_request["method"] == "POST"
            assert graph_request["url"] == "https://test-host.com:8182/openCypher"
    elif transformer_type == "loc_concepts":
        if destination == "void" or destination == "s3" or destination == "sns":
            assert len(MockRequest.calls) == 1
            request = MockRequest.calls[0]

            assert request["method"] == "GET"
            assert request["url"] == LOC_SUBJECT_HEADINGS_URL

        elif destination == "graph":
            assert len(MockRequest.calls) == 2
            loc_request = MockRequest.calls[0]

            assert loc_request["method"] == "GET"
            assert loc_request["url"] == LOC_SUBJECT_HEADINGS_URL

            graph_request = MockRequest.calls[1]

            assert graph_request["method"] == "POST"
            assert graph_request["url"] == "https://test-host.com:8182/openCypher"

    else:
        raise ValueError(
            f"Unsupported entity_type: {entity_type}, destination: {destination}, transformer_type: {transformer_type}"
        )
