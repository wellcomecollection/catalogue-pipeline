from test_utils import (
    add_mock_denormalised_documents,
    add_mock_transformer_outputs,
    check_bulk_load_edge,
)

from models.graph_edge import (
    ConceptHasSourceConcept,
    ConceptHasSourceConceptAttributes,
)
from models.graph_node import Concept
from transformers.catalogue.concepts_transformer import CatalogueConceptsTransformer


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations", "names"]
    )
    add_mock_denormalised_documents()

    transformer = CatalogueConceptsTransformer(None, True)
    nodes = list(transformer._stream_nodes())

    assert len(nodes) == 12
    assert any(
        item == Concept(id="s6s24vd7", label="Human anatomy", source="lc-subjects")
        for item in nodes
    )


def test_catalogue_concepts_transformer_edges() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations", "names"]
    )
    add_mock_denormalised_documents()

    transformer = CatalogueConceptsTransformer(None, True)

    edges = list(transformer._stream_edges())
    assert len(edges) == 7

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd7",
            to_id="sh85004839",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="yfqryj26",
            to_id="sh85045046",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd8",
            to_id="D000715",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd9",
            to_id="D000715",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier="Q000266", matched_by="identifier"
            ),
        ),
    )
