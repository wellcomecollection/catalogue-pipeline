from test_mocks import MockRequest
from test_utils import load_fixture

from sources.catalogue.concepts_source import CatalogueConceptsSource


def test_catalogue_concepts_source() -> None:
    test_url = "https://example.com"
    MockRequest.mock_response(
        method="GET",
        url=test_url,
        content_bytes=load_fixture("catalogue/works_snapshot_example.json"),
    )

    catalogue_concepts_source = CatalogueConceptsSource(test_url)
    stream_result = list(catalogue_concepts_source.stream_raw())

    # Do some simple checks on mesh source decoding based on known data
    assert len(stream_result) == 16
    assert stream_result[0][0]["label"] == "Human anatomy"
