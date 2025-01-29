from test_mocks import MockRequest
from test_utils import load_fixture

from sources.mesh.concepts_source import MeSHConceptsSource


def test_mesh_concepts_source() -> None:
    test_url = "https://example.com"
    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": test_url,
                "status_code": 200,
                "json_data": None,
                "content_bytes": load_fixture("mesh_example.xml"),
                "params": None,
            }
        ]
    )

    mesh_concepts_source = MeSHConceptsSource(test_url)
    stream_result = list(mesh_concepts_source.stream_raw())

    # Do some simple checks on mesh source decoding based on known data
    assert len(stream_result) == 7
    xml_elem, treenum_lookup = stream_result[0]

    assert xml_elem.tag == "DescriptorRecord"
    assert treenum_lookup["D02"] == "D009930"
