from models.events import BasePipelineEvent
from sources.catalogue.concepts_source import CatalogueConceptsSource
from tests.mocks import mock_es_secrets
from tests.test_utils import add_mock_merged_documents


def test_catalogue_concepts_source() -> None:
    add_mock_merged_documents("2025-05-05", work_status="Visible")
    mock_es_secrets("graph_extractor", "2025-05-05")

    catalogue_concepts_source = CatalogueConceptsSource(
        BasePipelineEvent(pipeline_date="2025-05-05")
    )
    stream_result = list(catalogue_concepts_source.stream_raw())

    # Do some simple checks on mesh source decoding based on known data
    assert len(stream_result) == 15
    assert any(item.concept.label == "Human anatomy" for item in stream_result)
