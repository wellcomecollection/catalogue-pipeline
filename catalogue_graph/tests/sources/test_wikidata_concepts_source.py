import json
from typing import Literal

from test_mocks import MockRequest
from test_utils import add_mock_transformer_outputs, load_fixture

from config import WIKIDATA_SPARQL_URL
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from utils.ontology_id_checker import is_id_classified_as_node_type, is_id_in_ontology


def _add_mock_wikidata_requests(
    entity_type: Literal["edges", "nodes"], node_type: Literal["concepts", "names"]
) -> None:
    """Add all the required mock Wikidata requests/responses based on whether we are streaming nodes or edges"""
    query_types = [
        "all_ids",
        "linked_ids",
        "instance_of",
        "subclass_of",
        "has_father",
        "has_mother",
        "has_sibling",
        "has_spouse",
        "has_child",
        "has_industry",
    ]
    if entity_type == "nodes":
        query_types.append(f"{node_type}/items")
    if node_type == "concepts":
        query_types.append("has_founder")
    if node_type == "names":
        query_types.append("has_field_of_work")

    for query_type in query_types:
        params = json.loads(
            load_fixture(f"wikidata_linked_loc/{query_type}_query.json")
        )
        response = json.loads(
            load_fixture(f"wikidata_linked_loc/{query_type}_response.json")
        )
        MockRequest.mock_response(
            method="GET", url=WIKIDATA_SPARQL_URL, params=params, json_data=response
        )


def test_wikidata_concepts_source_edges() -> None:
    add_mock_transformer_outputs(
        sources=["loc"], node_types=["concepts", "locations", "names"]
    )
    _add_mock_wikidata_requests("edges", "concepts")

    loc_concepts_source = WikidataLinkedOntologySource(
        node_type="concepts", linked_ontology="loc", entity_type="edges"
    )
    stream_result = list(loc_concepts_source.stream_raw())

    assert len(stream_result) == 5
    print(f"STREAM RESULT : {stream_result}")
    same_as_edges = set()
    has_parent_edges = set()
    has_industry_edges = set()
    has_founder_edges = set()
    for edge in stream_result:
        if edge["type"] == "SAME_AS":
            same_as_edges.add((edge["from_id"], edge["to_id"]))
        elif edge["type"] == "HAS_PARENT":
            has_parent_edges.add((edge["from_id"], edge["to_id"]))
        elif edge["type"] == "HAS_INDUSTRY":
            has_industry_edges.add((edge["from_id"], edge["to_id"]))
        elif edge["type"] == "HAS_FOUNDER":
            has_founder_edges.add((edge["from_id"], edge["to_id"]))
        else:
            raise ValueError(f"Unknown edge type {edge['type']}")

    assert len(same_as_edges) == 2
    assert ("Q1", "sh00000001") in same_as_edges
    assert ("Q2", "sh00000001") in same_as_edges

    assert len(has_parent_edges) == 3
    assert ("Q1", "Q4") in has_parent_edges
    assert ("Q2", "Q1") in has_parent_edges
    assert ("Q2", "Q3") in has_parent_edges

    assert len(has_founder_edges) == 1
    assert ("Q101", "Q100") in has_founder_edges

    assert len(has_industry_edges) == 1
    assert ("Q100", "Q2") in has_industry_edges


def test_wikidata_concepts_source_nodes() -> None:
    add_mock_transformer_outputs(
        sources=["loc"], node_types=["concepts", "locations", "names"]
    )
    _add_mock_wikidata_requests("nodes", "concepts")

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

    assert is_id_in_ontology("sh00000001", "loc")
    assert not is_id_in_ontology("sh00000001000", "loc")

    assert not is_id_classified_as_node_type("sh00000001", "loc", "locations")
    assert not is_id_classified_as_node_type("tgrefwdw", "loc", "locations")
    assert is_id_classified_as_node_type("sh00000015", "loc", "locations")
