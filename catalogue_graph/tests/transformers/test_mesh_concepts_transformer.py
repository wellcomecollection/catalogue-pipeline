from typing import cast

from test_mocks import MockRequest
from test_utils import load_fixture

from models.graph_edge import SourceConceptHasParent, SourceConceptRelatedTo
from models.graph_node import SourceConcept
from transformers.mesh.concepts_transformer import MeSHConceptsTransformer


def test_mesh_concepts_transformer() -> None:
    test_url = "https://example.com"

    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": test_url,
                "status_code": 200,
                "json_data": None,
                "content_bytes": load_fixture("mesh/raw_descriptors.xml"),
                "params": None,
            }
        ]
    )
    mesh_concepts_transformer = MeSHConceptsTransformer(test_url)

    # test transform_node
    nodes = list(mesh_concepts_transformer._stream_nodes())

    assert len(list(nodes)) == 3
    assert nodes[0] == SourceConcept(
        id="D009930",
        label="Organic Chemicals",
        source="nlm-mesh",
        alternative_ids=["D02"],
        alternative_labels=[
            "Chemicals, Organic",
            "Organic Chemical",
            "Chemical, Organic",
        ],
        description="A broad class of substances containing carbon and its derivatives. Many of these chemicals will frequently contain hydrogen with or without oxygen, nitrogen, sulfur, phosphorus, and other elements. They exist in either carbon chain or carbon ring form.\n    ",
    )
    assert len(cast(SourceConcept, nodes[2]).alternative_labels) == 20

    edges = list(mesh_concepts_transformer._stream_edges())

    assert edges[0] == SourceConceptHasParent(
        from_type="SourceConcept",
        to_type="SourceConcept",
        from_id="D004987",
        to_id="D009930",
        relationship="HAS_PARENT",
        directed=True,
    )

    assert edges[-1] == SourceConceptRelatedTo(
        from_type="SourceConcept",
        to_type="SourceConcept",
        from_id="D000009",
        to_id="D034861",
        relationship="RELATED_TO",
        directed=False,
    )
