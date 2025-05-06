from config import CATALOGUE_SNAPSHOT_URL
from models.graph_edge import (
    BaseEdge,
    ConceptHasSourceConcept,
    ConceptHasSourceConceptAttributes,
)
from models.graph_node import Concept
from test_mocks import MockRequest
from test_utils import add_mock_transformer_outputs, load_fixture
from transformers.catalogue.concepts_transformer import CatalogueConceptsTransformer


def _add_catalogue_request() -> None:
    MockRequest.mock_response(
        method="GET",
        url=CATALOGUE_SNAPSHOT_URL,
        content_bytes=load_fixture("catalogue/works_snapshot_example.json"),
    )


def _check_edge(
    all_edges: list[BaseEdge], from_id: str, to_id: str, expected_edge: BaseEdge
) -> None:
    filtered_edges = [
        edge for edge in all_edges if edge.from_id == from_id and edge.to_id == to_id
    ]
    assert len(filtered_edges) == 1
    assert filtered_edges[0] == expected_edge


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations", "names"]
    )
    _add_catalogue_request()

    catalogue_concepts_transformer = CatalogueConceptsTransformer(
        CATALOGUE_SNAPSHOT_URL
    )

    nodes = list(catalogue_concepts_transformer._stream_nodes())

    assert len(nodes) == 11
    assert nodes[0] == Concept(
        id="s6s24vd7", label="Human anatomy", source="lc-subjects"
    )


def test_catalogue_concepts_transformer_edges() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations", "names"]
    )
    _add_catalogue_request()

    catalogue_concepts_transformer = CatalogueConceptsTransformer(
        CATALOGUE_SNAPSHOT_URL
    )

    edges = list(catalogue_concepts_transformer._stream_edges())
    assert len(edges) == 6

    _check_edge(
        edges,
        "s6s24vd7",
        "sh85004839",
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

    _check_edge(
        edges,
        "yfqryj26",
        "sh00005643",
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="yfqryj26",
            to_id="sh00005643",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            ),
        ),
    )

    _check_edge(
        edges,
        "s6s24vd8",
        "D000715",
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

    _check_edge(
        edges,
        "s6s24vd9",
        "D000715",
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
