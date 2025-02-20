from test_mocks import MockRequest
from test_utils import add_mock_transformer_outputs, load_fixture

from config import CATALOGUE_SNAPSHOT_URL
from models.graph_edge import ConceptHasSourceConcept
from models.graph_node import Concept
from transformers.catalogue.concepts_transformer import CatalogueConceptsTransformer


def _add_catalogue_request() -> None:
    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": CATALOGUE_SNAPSHOT_URL,
                "status_code": 200,
                "json_data": None,
                "content_bytes": load_fixture("catalogue_example.json"),
                "params": None,
            }
        ]
    )


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations"]
    )
    _add_catalogue_request()

    catalogue_concepts_transformer = CatalogueConceptsTransformer(
        CATALOGUE_SNAPSHOT_URL
    )

    nodes = list(catalogue_concepts_transformer._stream_nodes())

    assert len(nodes) == 11
    assert nodes[0] == Concept(
        id="s6s24vd7", label="Human anatomy", type="Concept", source="lc-subjects"
    )


def test_catalogue_concepts_transformer_edges() -> None:
    add_mock_transformer_outputs(
        sources=["loc", "mesh"], node_types=["concepts", "locations"]
    )
    _add_catalogue_request()

    catalogue_concepts_transformer = CatalogueConceptsTransformer(
        CATALOGUE_SNAPSHOT_URL
    )

    edges = list(catalogue_concepts_transformer._stream_edges())

    assert len(edges) == 7
    assert edges[0] == ConceptHasSourceConcept(
        from_type="Concept",
        to_type="SourceConcept",
        from_id="s6s24vd7",
        to_id="sh85004839",
        relationship="HAS_SOURCE_CONCEPT",
        directed=True,
        attributes={"qualifier": None, "matched_by": "identifier"},
    )
    assert edges[2] == ConceptHasSourceConcept(
        from_type="Concept",
        to_type="SourceConcept",
        from_id="yfqryj26",
        to_id="sh85045046",
        relationship="HAS_SOURCE_CONCEPT",
        directed=True,
        attributes={"qualifier": None, "matched_by": "label"},
    )
    assert edges[3] == ConceptHasSourceConcept(
        from_type="Concept",
        to_type="SourceConcept",
        from_id="yfqryj26",
        to_id="sh00005643",
        relationship="HAS_SOURCE_CONCEPT",
        directed=True,
        attributes={"qualifier": None, "matched_by": "label"},
    )
    assert edges[5] == ConceptHasSourceConcept(
        from_type="Concept",
        to_type="SourceConcept",
        from_id="s6s24vd8",
        to_id="D000715",
        relationship="HAS_SOURCE_CONCEPT",
        directed=True,
        attributes={"qualifier": None, "matched_by": "identifier"},
    )
    assert edges[6] == ConceptHasSourceConcept(
        from_type="Concept",
        to_type="SourceConcept",
        from_id="s6s24vd9",
        to_id="D000715",
        relationship="HAS_SOURCE_CONCEPT",
        directed=True,
        attributes={"qualifier": "Q000266", "matched_by": "identifier"},
    )
