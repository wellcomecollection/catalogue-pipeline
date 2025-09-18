from collections.abc import Iterable
from itertools import chain
from typing import Any

import polars
import pydantic_core
import pytest
from test_mocks import MockElasticsearchClient, MockSecretsManagerClient, MockSmartOpen
from test_utils import load_fixture, load_json_fixture

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
)
from ingestor.steps.ingestor_indexer import handler
from utils.types import IngestorType


@pytest.mark.parametrize("record_type", ["concepts", "works"])
def test_ingestor_indexer_success(record_type: IngestorType) -> None:
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date="2025-01-01",
        index_date="2025-01-01",
        job_id="123",
    )

    _mock_es_secrets()

    # To regenerate this file after making ingestor changes, run the following command and retrieve the resulting file
    # from the `wellcomecollection-catalogue-graph` S3 bucket:
    # INGESTOR_SHARD_SIZE=10 AWS_PROFILE=platform-developer uv run src/ingestor/run_local.py --ingestor-type=concepts --limit=1
    MockSmartOpen.mock_s3_file(
        "s3://test-catalogue-graph/00000000-00000010.parquet",
        load_fixture(f"ingestor/{record_type}/00000000-00000010.parquet"),
    )
    MockSmartOpen.open(event.object_to_index.s3_uri, "r")

    expected_inputs = load_json_fixture(f"ingestor/{record_type}/mock_es_inputs.json")

    result = handler(event)
    assert len(MockElasticsearchClient.inputs) == 10
    assert result.success_count == 10
    assert MockElasticsearchClient.inputs == expected_inputs


def build_failure_test_matrix() -> Iterable[tuple]:
    concepts: IngestorType = "concepts"
    works: IngestorType = "works"
    return chain.from_iterable(
        [
            (
                failure_params_missing_file(correct_type),
                failure_params_malformed_parquet(correct_type),
                failure_params_wrong_content(correct_type, wrong_type),
            )
            for correct_type, wrong_type in [(concepts, works), (works, concepts)]
        ]
    )


def failure_params_missing_file(record_type: IngestorType) -> tuple:
    return (
        "the file at s3_uri doesn't exist",
        IngestorIndexerLambdaEvent(
            ingestor_type=record_type,
            pipeline_date="2021-07-01",
            index_date="2025-01-01",
            job_id="123",
        ),
        None,
        KeyError,
        "Mock S3 file s3://test-catalogue-graph/ghost-file does not exist.",
    )


def failure_params_malformed_parquet(record_type: IngestorType) -> tuple:
    return (
        "the S3 file is malformed",
        IngestorIndexerLambdaEvent(
            ingestor_type=record_type,
            pipeline_date="2021-07-01",
            index_date="2025-01-01",
            job_id="123",
        ),
        "catalogue/denormalised_works_example.jsonl",
        polars.exceptions.ComputeError,
        "parquet: File out of specification: The file must end with PAR1",
    )


def failure_params_wrong_content(
    expected_type: IngestorType, actual_type: IngestorType
) -> tuple:
    return (
        "the S3 file contains invalid data",
        IngestorIndexerLambdaEvent(
            ingestor_type=expected_type,
            pipeline_date="2021-07-01",
            index_date="2025-01-01",
            job_id="123",
        ),
        f"ingestor/{actual_type}/00000000-00000010.parquet",
        pydantic_core.ValidationError,
        "\\d+ validation errors for Indexable.*",
    )


def get_test_id(argvalue: str) -> str:
    return argvalue


@pytest.mark.parametrize(
    "description,event,fixture,expected_error,error_message",
    build_failure_test_matrix(),
    ids=get_test_id,
)
def test_ingestor_indexer_failure(
    description: str,
    event: IngestorIndexerLambdaEvent,
    fixture: str,
    expected_error: Any | tuple,
    error_message: str,
) -> None:
    with pytest.raises(expected_exception=expected_error, match=error_message):
        if description != "the file at s3_uri doesn't exist":
            MockSmartOpen.mock_s3_file(
                event.object_to_index.s3_uri, load_fixture(fixture)
            )
        MockSmartOpen.open(event.object_to_index.s3_uri, "r")

        handler(event)


def _mock_es_secrets() -> None:
    # Using a non-null pipeline_date connects to the production ES cluster, so we need to mock some secrets
    MockSecretsManagerClient.add_mock_secret(
        "elasticsearch/pipeline_storage_2025-01-01/private_host", "test"
    )
    MockSecretsManagerClient.add_mock_secret(
        "elasticsearch/pipeline_storage_2025-01-01/port", 80
    )
    MockSecretsManagerClient.add_mock_secret(
        "elasticsearch/pipeline_storage_2025-01-01/protocol", "http"
    )
    MockSecretsManagerClient.add_mock_secret(
        "elasticsearch/pipeline_storage_2025-01-01/concept_ingestor/api_key", ""
    )
