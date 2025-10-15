from models.graph_node import SourceLocation
from tests.mocks import MockRequest
from tests.test_utils import load_fixture
from transformers.mesh.locations_transformer import MeSHLocationsTransformer


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
    mesh_concepts_transformer = MeSHLocationsTransformer(test_url)

    # test transform_node
    nodes = list(mesh_concepts_transformer._stream_nodes())

    assert len(list(nodes)) == 1
    assert nodes[0] == SourceLocation(
        id="D008131",
        label="London",
        source="nlm-mesh",
        alternative_ids=["Z01"],
        alternative_labels=[],
        description="The capital of the United Kingdom. It is located in England.\n    ",
        latitude=None,
        longitude=None,
    )
