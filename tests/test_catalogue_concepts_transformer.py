from test_mocks import MockRequest
from test_utils import load_fixture

from transformers.catalogue.concepts_transformer import CatalogueConceptsTransformer


def test_mesh_concepts_transformer() -> None:
    test_url = "https://example.com"

    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": test_url,
                "status_code": 200,
                "json_data": None,
                "content_bytes": load_fixture("catalogue_example.json"),
                "params": None,
            }
        ]
    )
    catalogue_concepts_transformer = CatalogueConceptsTransformer(test_url)

    # test transform_node
    nodes = list(
        catalogue_concepts_transformer.stream(entity_type="nodes", query_chunk_size=1)
    )
    assert len(list(nodes)) == 12
    assert nodes[0][0].id == "s6s24vd7"
    assert nodes[0][0].label == "Human anatomy"
    assert nodes[0][0].type == "Concept"
    assert nodes[0][0].source == "lc-subjects"
