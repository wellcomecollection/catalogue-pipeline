import json
from typing import Literal

from test_mocks import MockRequest
from test_utils import add_mock_transformer_outputs, load_fixture

from config import WIKIDATA_SPARQL_URL
from sources.wikidata.linked_ontology_id_type_checker import LinkedOntologyIdTypeChecker
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource


def _add_mock_wikidata_requests(node_type: Literal["edges", "nodes"]) -> None:
    """Add all the required mock Wikidata requests/responses based on whether we are streaming nodes or edges"""
    query_types = [
        "all_ids",
        "linked_ids",
        "parents_instance_of",
        "parents_subclass_of",
    ]
    if node_type == "nodes":
        query_types.append("items")

    for query_type in query_types:
        params = json.loads(load_fixture(f"wikidata/{query_type}_query.json"))
        response = json.loads(load_fixture(f"wikidata/{query_type}_response.json"))
        MockRequest.mock_response(
            method="GET", url=WIKIDATA_SPARQL_URL, params=params, json_data=response
        )


def test_wikidata_concepts_source_edges() -> None:
    add_mock_transformer_outputs(
        sources=["loc"], node_types=["concepts", "locations", "names"]
    )
    _add_mock_wikidata_requests("edges")

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
    add_mock_transformer_outputs(
        sources=["loc"], node_types=["concepts", "locations", "names"]
    )
    _add_mock_wikidata_requests("nodes")

    mesh_concepts_source = WikidataLinkedOntologySource(
        node_type="concepts", linked_ontology="loc", entity_type="nodes"
    )

    stream_result = list(mesh_concepts_source.stream_raw())

    assert len(stream_result) == 4

    for raw_node in stream_result:
        assert "item" in raw_node
        assert "itemLabel" in raw_node
        assert "itemDescription" in raw_node


def test_wikidata_linked_ontology_id_checker() -> None:
    add_mock_transformer_outputs(
        sources=["loc"], node_types=["concepts", "locations", "names"]
    )
    id_checker = LinkedOntologyIdTypeChecker("locations", "loc")

    assert id_checker.id_is_valid("sh00000001")
    assert not id_checker.id_is_valid("sh00000001000")

    assert not id_checker.id_included_in_selected_type("sh00000001")
    assert not id_checker.id_included_in_selected_type("tgrefwdw")
    assert id_checker.id_included_in_selected_type("sh00000015")
