import polars
import pydantic_core
import pytest

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
)
from ingestor.steps.ingestor_indexer import handler
from tests.mocks import (
    MockElasticsearchClient,
    MockSmartOpen,
    mock_es_secrets,
)
from tests.test_utils import load_fixture, load_json_fixture
from utils.types import IngestorType


def get_mock_indexer_event(record_type: IngestorType) -> IngestorIndexerLambdaEvent:
    return IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date="2021-07-01",
        index_date="2025-01-01",
        job_id="123",
        objects_to_index=[
            IngestorIndexerObject(
                s3_uri="s3://test-catalogue-graph/merged_works/sample.jsonl",
                content_length=10,
                record_count=10,
            )
        ],
    )


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_success(record_type: IngestorType) -> None:
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date="2025-01-01",
        index_date="2025-01-01",
        job_id="123",
        objects_to_index=[
            IngestorIndexerObject(
                s3_uri="s3://test-catalogue-graph/00000000-00000010.parquet",
                record_count=10,
                content_length=500,
            )
        ],
    )

    # TO DO: generate a new parquet file the matches the RelatedConcepts model once the graph as been updated
    mock_es_secrets(f"{record_type}_ingestor", "2025-01-01")

    # To regenerate this file after making ingestor changes, run the following command and retrieve the resulting file
    # from the `wellcomecollection-catalogue-graph` S3 bucket:
    # INGESTOR_SHARD_SIZE=10 AWS_PROFILE=platform-developer uv run src/ingestor/run_local.py --ingestor-type=concepts --limit=1
    MockSmartOpen.mock_s3_file(
        "s3://test-catalogue-graph/00000000-00000010.parquet",
        load_fixture(f"ingestor/{record_type}/00000000-00000010.parquet"),
    )

    expected_inputs = load_json_fixture(f"ingestor/{record_type}/mock_es_inputs.json")

    result = handler(event)
    assert len(MockElasticsearchClient.inputs) == 10
    assert result.success_count == 10
    assert MockElasticsearchClient.inputs == expected_inputs


def test_ingestor_indexer_failure_invalid_data_concepts() -> None:
    # Run works indexer but mock concepts file
    event = get_mock_indexer_event("concepts")
    MockSmartOpen.mock_s3_file(
        event.objects_to_index[0].s3_uri,
        load_fixture("ingestor/works/00000000-00000010.parquet"),
    )

    with pytest.raises(
        expected_exception=pydantic_core.ValidationError,
        match="\\d+ validation errors for IndexableConcept.*",
    ):
        handler(event)


def test_ingestor_indexer_failure_invalid_data_works() -> None:
    # Run concepts indexer but mock works file
    event = get_mock_indexer_event("works")
    MockSmartOpen.mock_s3_file(
        event.objects_to_index[0].s3_uri,
        load_fixture("ingestor/concepts/00000000-00000010.parquet"),
    )

    with pytest.raises(expected_exception=KeyError, match="'type'"):
        handler(event)


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_failure_invalid_parquet(record_type: IngestorType) -> None:
    event = get_mock_indexer_event(record_type)
    MockSmartOpen.mock_s3_file(
        event.objects_to_index[0].s3_uri,
        load_fixture("merged_works/sample.jsonl"),
    )

    with pytest.raises(expected_exception=polars.exceptions.ComputeError):
        handler(event)


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_failure_missing_file(record_type: IngestorType) -> None:
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date="2021-07-01",
        index_date="2025-01-01",
        job_id="123",
        objects_to_index=[
            IngestorIndexerObject(
                s3_uri="s3://test-catalogue-graph/ghost-file",
                content_length=0,
                record_count=0,
            )
        ],
    )

    with pytest.raises(
        expected_exception=KeyError,
        match="Mock S3 file s3://test-catalogue-graph/ghost-file does not exist.",
    ):
        handler(event)
