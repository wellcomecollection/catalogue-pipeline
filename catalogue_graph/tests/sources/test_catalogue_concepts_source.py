from test_utils import add_mock_denormalised_documents

from sources.catalogue.concepts_source import CatalogueConceptsSource


def test_catalogue_concepts_source() -> None:
    add_mock_denormalised_documents()

    catalogue_concepts_source = CatalogueConceptsSource(None)
    stream_result = list(catalogue_concepts_source.stream_raw())

    # Do some simple checks on mesh source decoding based on known data
    assert len(stream_result) == 15
    assert any(item[0]["label"] == "Human anatomy" for item in stream_result)
