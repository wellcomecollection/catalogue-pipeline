from graph.sources.wikidata.linked_ontology_edge_source import (
    WikidataLinkedOntologyEdgeSource,
)
from graph.sources.wikidata.linked_ontology_node_source import (
    WikidataLinkedOntologyNodeSource,
)
from models.events import ExtractorEvent
from tests.graph.sources.test_wikidata_concepts_source import (
    _add_mock_wikidata_requests,
)
from tests.test_utils import (
    add_mock_transformer_outputs_for_ontologies,
)


def test_wikidata_names_source_edges() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc", "wikidata_linked_loc"])

    _add_mock_wikidata_requests("edges", "names")

    source_event = ExtractorEvent(
        pipeline_date="dev",
        environment="prod",
        transformer_type="wikidata_linked_loc_names",
        entity_type="edges",
    )
    mesh_concepts_source = WikidataLinkedOntologyEdgeSource(
        linked_transformer="loc_names", event=source_event
    )
    stream_result = list(mesh_concepts_source.stream_raw())
    assert len(stream_result) == 6

    same_as_edges = set()
    has_field_of_work_edges = set()
    related_to_edges = set()
    has_industry_edges = set()
    for edge, edge_type in stream_result:
        if edge_type in ("same_as_loc", "same_as_mesh"):
            same_as_edges.add((edge["from_id"], edge["to_id"]))
        elif edge_type == "has_field_of_work":
            has_field_of_work_edges.add((edge["from_id"], edge["to_id"]))
        elif edge_type in (
            "has_father",
            "has_mother",
            "has_sibling",
            "has_spouse",
            "has_child",
        ):
            related_to_edges.add((edge["from_id"], edge["to_id"], edge_type))
        elif edge_type == "has_industry":
            has_industry_edges.add((edge["from_id"], edge["to_id"]))
        else:
            raise ValueError(f"Unknown edge type {edge_type}")

    assert len(same_as_edges) == 2
    assert ("Q100", "n00000001") in same_as_edges
    assert ("Q101", "n00000021") in same_as_edges

    assert len(has_field_of_work_edges) == 1
    assert ("Q100", "Q1") in has_field_of_work_edges

    assert len(related_to_edges) == 2
    assert ("Q100", "Q101", "has_mother") in related_to_edges
    assert ("Q101", "Q100", "has_child") in related_to_edges

    assert len(has_industry_edges) == 1
    assert ("Q100", "Q2") in has_industry_edges


def test_wikidata_names_source_nodes() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc"])
    _add_mock_wikidata_requests("nodes", "names")

    source_event = ExtractorEvent(
        pipeline_date="dev",
        environment="prod",
        transformer_type="wikidata_linked_loc_names",
        entity_type="nodes",
    )
    mesh_concepts_source = WikidataLinkedOntologyNodeSource(
        linked_transformer="loc_names", event=source_event
    )
    stream_result = list(mesh_concepts_source.stream_raw())

    assert len(stream_result) == 2

    for raw_node in stream_result:
        assert "item" in raw_node
        assert "itemLabel" in raw_node
        assert "itemDescription" in raw_node
        assert "placeOfBirthLabel" in raw_node
        assert "dateOfBirth" in raw_node

    assert "dateOfDeath" in stream_result[1]
    assert "dateOfDeath" not in stream_result[0]
