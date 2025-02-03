from test_wikidata_concepts_source import (
    _add_mock_wikidata_requests,
    _add_mock_loc_transformer_outputs,
)

from models.graph_node import SourceConcept
from models.graph_edge import SourceConceptSameAs
from transformers.wikidata.concepts_transformer import WikidataConceptsTransformer


def test_wikidata_concepts_nodes_transformer() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests(
        ["all_ids", "linked_ids", "parents_instance_of", "parents_subclass_of", "items"]
    )

    transformer = WikidataConceptsTransformer(
        entity_type="nodes", linked_ontology="loc"
    )

    nodes = list(transformer.stream(entity_type="nodes", query_chunk_size=100))[0]

    assert len(list(nodes)) == 4

    assert nodes[0] == SourceConcept(
        id="Q1",
        label="Eustigmatophyceae",
        source="wikidata",
        alternative_ids=[],
        alternative_labels=[],
        description="class of algae",
    )


def test_wikidata_concepts_edges_transformer() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests(
        ["all_ids", "linked_ids", "parents_instance_of", "parents_subclass_of"]
    )

    transformer = WikidataConceptsTransformer(
        entity_type="edges", linked_ontology="loc"
    )

    edges = list(transformer.stream(entity_type="edges", query_chunk_size=100))[0]
    print(edges)
    assert len(list(edges)) == 7

    assert edges[0] == SourceConceptSameAs(
        from_type="SourceConcept",
        to_type="SourceConcept",
        from_id="sh00000001",
        to_id="Q1",
        relationship="SAME_AS",
        directed=False,
        attributes={"source": "wikidata"},
    )
