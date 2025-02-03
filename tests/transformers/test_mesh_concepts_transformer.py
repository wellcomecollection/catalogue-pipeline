from test_mocks import MockRequest
from test_utils import load_fixture

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
    nodes = list(
        mesh_concepts_transformer.stream(entity_type="nodes", query_chunk_size=1)
    )
    assert len(list(nodes)) == 7
    assert nodes[0][0].id == "D009930"
    assert nodes[0][0].label == "Organic Chemicals"

    stream = mesh_concepts_transformer.stream(entity_type="edges", query_chunk_size=1)
    # get first element, trying to get all of them will get edges we don't have in the test data
    first_chunk = stream.__next__()
    first_element = first_chunk[0]

    assert first_element.from_type == "SourceConcept"
    assert first_element.to_type == "SourceConcept"
    assert first_element.from_id == "D004987"
    assert first_element.to_id == "D009930"
    assert first_element.relationship == "HAS_PARENT"
