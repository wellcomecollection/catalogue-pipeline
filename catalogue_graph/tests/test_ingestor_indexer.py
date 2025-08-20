import json
from typing import Any

import polars
import pytest
from test_mocks import MockElasticsearchClient, MockSecretsManagerClient, MockSmartOpen
from test_utils import load_fixture

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
)
from ingestor.steps.ingestor_indexer import IngestorIndexerConfig, handler
from ingestor.models.indexable_concept import IndexableConcept
import pydantic_core
from itertools import chain


@pytest.mark.parametrize(
    "record_type",
    ["concepts", "works"]
)
def test_ingestor_indexer_success(record_type: str) -> None:
    config = IngestorIndexerConfig()
    event = IngestorIndexerLambdaEvent(
        ingestor_type=record_type,
        pipeline_date="2025-01-01",
        index_date="2025-01-01",
        job_id="123",
        object_to_index=IngestorIndexerObject(
            s3_uri="s3://test-catalogue-graph/00000000-00000010.parquet"
        ),
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

    expected_inputs = json.loads(load_fixture(f"ingestor/{record_type}/mock_es_inputs.json"))

    result = handler(event, config)
    assert len(MockElasticsearchClient.inputs) == 10
    assert result.success_count == 10
    assert MockElasticsearchClient.inputs == expected_inputs


def build_failure_test_matrix() -> list[tuple]:
    return chain.from_iterable([[
        (
            "the file at s3_uri doesn't exist",
            IngestorIndexerLambdaEvent(
                ingestor_type=correct_type,
                pipeline_date="2021-07-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://test-catalogue-graph/ghost-file"
                ),
            ),
            None,
            KeyError,
            "Mock S3 file s3://test-catalogue-graph/ghost-file does not exist.",
        ),
        (
            "the S3 file is malformed",
            IngestorIndexerLambdaEvent(
                ingestor_type=correct_type,
                pipeline_date="2021-07-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://test-catalogue-graph/catalogue/denormalised_works_example.jsonl"
                ),
            ),
            "catalogue/denormalised_works_example.jsonl",
            polars.exceptions.ComputeError,
            "parquet: File out of specification: The file must end with PAR1",
        ),
        (
            "the S3 file contains invalid data",
            IngestorIndexerLambdaEvent(
                ingestor_type=correct_type,
                pipeline_date="2021-07-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://test-catalogue-graph/catalogue/00000000-00000010.parquet"
                ),
            ),
            f"ingestor/{wrong_type}/00000000-00000010.parquet",
            pydantic_core.ValidationError,
            "\\d+ validation errors for Indexable.*",
        )
    ] for correct_type, wrong_type in [("concepts", "works"), ("works", "concepts")]])


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
    config = IngestorIndexerConfig()

    with pytest.raises(expected_exception=expected_error, match=error_message):
        if description != "the file at s3_uri doesn't exist":
            MockSmartOpen.mock_s3_file(
                event.object_to_index.s3_uri, load_fixture(fixture)
            )
        MockSmartOpen.open(event.object_to_index.s3_uri, "r")

        handler(event, config)


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
