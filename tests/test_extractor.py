from typing import Any, Generator

import pytest
from test_mocks import MOCK_INSTANCE_ENDPOINT, MockRequest, MockResponseInput
from test_utils import load_fixture
from typing_extensions import get_args

from config import (
    LOC_NAMES_URL,
    LOC_SUBJECT_HEADINGS_URL,
    MESH_URL,
    WIKIDATA_SPARQL_URL,
    CATALOGUE_SNAPSHOT_URL
)
from extractor import LambdaEvent, lambda_handler
from transformers.base_transformer import EntityType, StreamDestination
from transformers.create_transformer import TransformerType

transformer_types = get_args(TransformerType)
entity_types = get_args(EntityType)
stream_destinations = get_args(StreamDestination)


def mock_requests_lookup_table(
    destination: StreamDestination,
    transformer_type: TransformerType,
) -> Any:

    mocked_responses: list[dict] = []

    if destination == "graph":
        mocked_responses.append(
            {
                "method": "POST",
                "url": f"https://{MOCK_INSTANCE_ENDPOINT}:8182/openCypher",
                "content_bytes": None,
                "json_data": {"results": {}},
            }
        )

    if transformer_type in ["mesh_concepts", "mesh_locations"]:
        mocked_responses.append(
            {
                "method": "GET",
                "url": MESH_URL,
                "content_bytes": load_fixture("mesh_example.xml"),
            }
        )
    elif transformer_type in ["loc_concepts", "loc_locations", "loc_names"]:
        mocked_responses.append(
            {
                "method": "GET",
                "url": LOC_SUBJECT_HEADINGS_URL,
                "content_bytes": load_fixture("loc_subjects_example.jsonld"),
            }
        )
        mocked_responses.append(
            {
                "method": "GET",
                "url": LOC_NAMES_URL,
                "content_bytes": load_fixture("loc_names_example.jsonld"),
            }
        )
    elif transformer_type in [
        "wikidata_linked_loc_names",
        "wikidata_linked_loc_concepts",
        "wikidata_linked_loc_locations",
        "wikidata_linked_loc_names",
    ]:
        mocked_responses.append(
            {
                "method": "GET",
                "url": WIKIDATA_SPARQL_URL,
                "params": {
                    "format": "json",
                    "query": "SELECT ?item WHERE { ?item wdt:P244 _:anyValueP244. }",
                },
                "content_bytes": None,
                "json_data": {"results": {"bindings": []}},
            }
        )
    elif transformer_type in [
        "wikidata_linked_mesh_concepts",
        "wikidata_linked_mesh_locations",
    ]:
        mocked_responses.append(
            {
                "method": "GET",
                "url": WIKIDATA_SPARQL_URL,
                "params": {
                    "format": "json",
                    "query": "SELECT ?item WHERE { ?item wdt:P486 _:anyValueP486. }",
                },
                "content_bytes": None,
                "json_data": {"results": {"bindings": []}},
            }
        )
    elif transformer_type == "catalogue_concepts":
        mocked_responses.append(
            {
                "method": "GET",
                "url": CATALOGUE_SNAPSHOT_URL,
                "content_bytes": load_fixture("catalogue_example.json"),
            }
        )

    return mocked_responses


def build_test_matrix() -> Generator[tuple[LambdaEvent, list[MockResponseInput]], Any]:
    for transformer_type in transformer_types:
        for entity_type in entity_types:
            for stream_destination in stream_destinations:
                yield (
                    {
                        "transformer_type": transformer_type,
                        "entity_type": entity_type,
                        "stream_destination": stream_destination,
                        "sample_size": 1,
                    },
                    mock_requests_lookup_table(stream_destination, transformer_type),
                )


def get_test_id(argvalue: Any) -> str:
    if isinstance(argvalue, list):
        return ""
    return f"{argvalue['transformer_type']}-{argvalue['entity_type']}-{argvalue['stream_destination']}"


@pytest.mark.parametrize(
    "lambda_event, mock_responses",
    build_test_matrix(),
    ids=get_test_id,
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

    concept_retrieval_url_lookup = {
        "mesh_concepts": [MESH_URL],
        "mesh_locations": [MESH_URL],
        "loc_concepts": [LOC_SUBJECT_HEADINGS_URL],
        "loc_locations": [LOC_NAMES_URL, LOC_SUBJECT_HEADINGS_URL],
        "loc_names": [LOC_NAMES_URL],
        "wikidata_linked_loc_names": [WIKIDATA_SPARQL_URL],
        "wikidata_linked_loc_concepts": [WIKIDATA_SPARQL_URL],
        "wikidata_linked_loc_locations": [WIKIDATA_SPARQL_URL],
        "wikidata_linked_mesh_concepts": [WIKIDATA_SPARQL_URL],
        "wikidata_linked_mesh_locations": [WIKIDATA_SPARQL_URL],
        "catalogue_concepts": [CATALOGUE_SNAPSHOT_URL]
    }

    assert transformer_type in transformer_types
    assert destination in stream_destinations
    assert entity_type in entity_types

    concept_retrieval_urls = concept_retrieval_url_lookup[transformer_type]
    called_urls = [call["url"] for call in MockRequest.calls]

    assert all(
        concept_retrieval_url in called_urls
        for concept_retrieval_url in concept_retrieval_urls
    ), (
        f"Unexpected requests found for ({transformer_type}, {entity_type}, {destination}): "
        + f"Expected concept retrieval URLs: {concept_retrieval_urls}, got: {called_urls}"
    )
