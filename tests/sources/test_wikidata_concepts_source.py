from test_mocks import MockRequest, MockSmartOpen
import json
from typing import Literal
from config import WIKIDATA_SPARQL_URL

from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from test_utils import load_fixture


WikidataQueryType = Literal[
    "all_ids", "linked_ids", "parents_instance_of", "parents_subclass_of", "items"
]


def _add_mock_wikidata_requests(query_types: list[WikidataQueryType]) -> None:
    for query_type in query_types:
        params = json.loads(load_fixture(f"wikidata/{query_type}_query.json"))
        response = json.loads(load_fixture(f"wikidata/{query_type}_response.json"))
        MockRequest.mock_response(
            method="GET", url=WIKIDATA_SPARQL_URL, params=params, json_data=response
        )


def _add_mock_loc_transformer_outputs() -> None:
    for node_type in ["concepts", "locations", "names"]:
        MockSmartOpen.mock_s3_file(
            f"s3://bulk_load_test_bucket/loc_{node_type}__nodes.csv",
            load_fixture(f"loc/transformer_output_{node_type}_nodes.csv").decode(),
        )


def test_wikidata_concepts_source_edges() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests(
        ["all_ids", "linked_ids", "parents_instance_of", "parents_subclass_of"]
    )

    mesh_concepts_source = WikidataLinkedOntologySource(
        node_type="concepts", linked_ontology="loc", entity_type="edges"
    )
    stream_result = list(mesh_concepts_source.stream_raw())

    assert len(stream_result) == 5

    same_as_edges = set()
    has_parent_edges = set()
    for edge in stream_result:
        if edge["type"] == "SAME_AS":
            same_as_edges.add((edge["wikidata_id"], edge["linked_id"]))
        elif edge["type"] == "HAS_PARENT":
            has_parent_edges.add((edge["child_id"], edge["parent_id"]))
        else:
            raise ValueError(f"Unknown edge type {edge['type']}")

    assert len(same_as_edges) == 2
    assert ("Q1", "sh00000001") in same_as_edges
    assert ("Q2", "sh00000001") in same_as_edges

    assert len(has_parent_edges) == 3
    assert ("Q1", "Q4") in has_parent_edges
    assert ("Q2", "Q1") in has_parent_edges
    assert ("Q2", "Q3") in has_parent_edges


def test_wikidata_concepts_source_nodes() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests(
        ["all_ids", "linked_ids", "parents_instance_of", "parents_subclass_of", "items"]
    )

    mesh_concepts_source = WikidataLinkedOntologySource(
        node_type="concepts", linked_ontology="loc", entity_type="nodes"
    )

    stream_result = list(mesh_concepts_source.stream_raw())

    assert len(stream_result) == 4

    for raw_node in stream_result:
        assert "item" in raw_node
        assert "itemLabel" in raw_node
        assert "itemDescription" in raw_node
