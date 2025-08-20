from test_utils import add_mock_transformer_outputs
from test_wikidata_concepts_source import _add_mock_wikidata_requests

from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource


def test_wikidata_names_source_edges() -> None:
    add_mock_transformer_outputs(["loc", "wikidata_linked_loc"])
    _add_mock_wikidata_requests("edges", "names")

    mesh_concepts_source = WikidataLinkedOntologySource(
        linked_transformer="loc_names", entity_type="edges"
    )
    stream_result = list(mesh_concepts_source.stream_raw())

    assert len(stream_result) == 5

    same_as_edges = set()
    has_field_of_work_edges = set()
    related_to_edges = set()
    for edge in stream_result:
        if edge["type"] == "SAME_AS":
            same_as_edges.add((edge["from_id"], edge["to_id"]))
        elif edge["type"] == "HAS_FIELD_OF_WORK":
            has_field_of_work_edges.add((edge["from_id"], edge["to_id"]))
        elif edge["type"] == "RELATED_TO":
            related_to_edges.add((edge["from_id"], edge["to_id"], edge["subtype"]))
        else:
            raise ValueError(f"Unknown edge type {edge['type']}")

    assert len(same_as_edges) == 2
    assert ("Q100", "n00000001") in same_as_edges
    assert ("Q101", "n00000021") in same_as_edges

    assert len(has_field_of_work_edges) == 1
    assert ("Q100", "Q1") in has_field_of_work_edges

    assert len(related_to_edges) == 2
    assert ("Q100", "Q101", "has_mother") in related_to_edges
    assert ("Q101", "Q100", "has_child") in related_to_edges


def test_wikidata_names_source_nodes() -> None:
    add_mock_transformer_outputs(["loc"])
    _add_mock_wikidata_requests("nodes", "names")

    mesh_concepts_source = WikidataLinkedOntologySource(
        linked_transformer="loc_names", entity_type="nodes"
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
