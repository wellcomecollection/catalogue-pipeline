import json
from typing import Any, cast

import polars
import pydantic_core
import pytest

import config
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
)
from ingestor.steps.ingestor_indexer import handler
from tests.mocks import (
    MockElasticsearchClient,
    MockS3Client,
    MockSmartOpen,
    mock_es_secrets,
)
from tests.test_utils import load_fixture, load_json_fixture
from utils.types import IngestorType


def _get_result_s3_uri(event: IngestorIndexerLambdaEvent) -> str:
    return event.get_s3_uri("report.indexer", "json")


def _read_indexer_report(event: IngestorIndexerLambdaEvent) -> dict[str, Any]:
    with MockSmartOpen.open(_get_result_s3_uri(event), "r") as f:
        return cast(dict[str, Any], json.load(f))


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
def test_ingestor_indexer_discovers_parquet_objects(record_type: IngestorType) -> None:
    pipeline_date = "2025-01-01"
    index_date = "2025-01-01"
    job_id = "20250930T0930"
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        objects_to_index=None,
    )

    prefix = event.get_path_prefix()
    bucket = config.CATALOGUE_GRAPH_S3_BUCKET
    key = f"{prefix}/00000000-00000010.parquet"
    s3_uri = f"s3://{bucket}/{key}"

    parquet_bytes = load_fixture(f"ingestor/{record_type}/00000000-00000010.parquet")
    MockS3Client.add_list_objects_response(
        bucket=bucket,
        prefix=prefix,
        contents=[{"Key": key, "Size": len(parquet_bytes)}],
    )
    MockSmartOpen.mock_s3_file(s3_uri, parquet_bytes)

    mock_es_secrets(f"{record_type}_ingestor", pipeline_date)
    expected_inputs = load_json_fixture(f"ingestor/{record_type}/mock_es_inputs.json")

    result = handler(event)

    assert MockS3Client.list_objects_v2_calls == [(bucket, prefix)]
    assert result.success_count == len(expected_inputs)
    assert MockElasticsearchClient.inputs == expected_inputs

    report = _read_indexer_report(event)
    assert report == {
        "pipeline_date": pipeline_date,
        "index_date": index_date,
        "ingestor_type": record_type,
        "job_id": job_id,
        "load_format": "parquet",
        "window": None,
        "pit_id": None,
        "success_count": len(expected_inputs),
    }


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_handles_explicit_objects(record_type: IngestorType) -> None:
    pipeline_date = "2025-01-01"
    index_date = "2025-01-01"
    job_id = "20250930T0930"
    prefix = (
        f"{config.INGESTOR_S3_PREFIX}_{record_type}/"
        f"{pipeline_date}/{index_date}/{job_id}"
    )
    bucket = config.CATALOGUE_GRAPH_S3_BUCKET
    key = f"{prefix}/00000000-00000010.parquet"
    s3_uri = f"s3://{bucket}/{key}"

    parquet_bytes = load_fixture(f"ingestor/{record_type}/00000000-00000010.parquet")
    MockSmartOpen.mock_s3_file(s3_uri, parquet_bytes)

    expected_inputs = load_json_fixture(f"ingestor/{record_type}/mock_es_inputs.json")
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        objects_to_index=[
            IngestorIndexerObject(
                s3_uri=s3_uri,
                content_length=len(parquet_bytes),
                record_count=len(expected_inputs),
            )
        ],
    )

    # TO DO: generate a new parquet file that matches the RelatedConcepts model once the graph has been updated
    mock_es_secrets(f"{record_type}_ingestor", pipeline_date)

    result = handler(event)

    assert MockS3Client.list_objects_v2_calls == []
    assert result.success_count == len(expected_inputs)
    assert MockElasticsearchClient.inputs == expected_inputs

    report = _read_indexer_report(event)
    assert report == {
        "pipeline_date": pipeline_date,
        "index_date": index_date,
        "ingestor_type": record_type,
        "job_id": job_id,
        "load_format": "parquet",
        "window": None,
        "pit_id": None,
        "success_count": len(expected_inputs),
    }


def test_ingestor_indexer_failure_invalid_data_concepts() -> None:
    # Run works indexer but mock concepts file
    event = get_mock_indexer_event("concepts")
    assert event.objects_to_index is not None
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
    assert event.objects_to_index is not None
    MockSmartOpen.mock_s3_file(
        event.objects_to_index[0].s3_uri,
        load_fixture("ingestor/concepts/00000000-00000010.parquet"),
    )

    with pytest.raises(expected_exception=KeyError, match="'type'"):
        handler(event)


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_failure_invalid_parquet(record_type: IngestorType) -> None:
    event = get_mock_indexer_event(record_type)
    assert event.objects_to_index is not None
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
