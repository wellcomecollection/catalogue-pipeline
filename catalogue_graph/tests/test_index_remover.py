import json

import polars as pl
import pytest
from test_graph_remover import CATALOGUE_CONCEPTS_REMOVED_IDS_URI
from test_mocks import MockElasticsearchClient, MockSecretsManagerClient, MockSmartOpen

from graph_remover import IDS_LOG_SCHEMA
from index_remover import lambda_handler
from models.step_events import ReporterEvent


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


def index_concepts(ids: list[str], index_name: str = "concepts-indexed") -> None:
    for _id in ids:
        MockElasticsearchClient.index(index_name, _id, {})


def mock_deleted_ids_log_file() -> None:
    mock_data = {
        "timestamp": ["2025-04-03", "2025-04-03", "2025-04-07", "2025-04-07"],
        "id": ["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"],
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, df)


def test_index_remover_first_run() -> None:
    mock_deleted_ids_log_file()

    # Index some empty documents with the same IDs as those stored in the parquet mock
    # (plus an extra document which shouldn't be removed).
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    indexed_concepts = MockElasticsearchClient.indexed_documents["concepts-indexed"]
    assert len(indexed_concepts) == 5

    # No index date specified, so the local 'concepts-indexed' index name should be used
    event = ReporterEvent(
        pipeline_date=None,
        index_date=None,
        job_id=None,
        success_count=1000,
        force_pass=True,
    )
    
    lambda_handler([event], None)

    indexed_concepts = MockElasticsearchClient.indexed_documents["concepts-indexed"]

    assert len(indexed_concepts) == 1
    assert list(indexed_concepts.keys())[0] == "someid12"


def test_index_remover_next_run() -> None:
    mock_deleted_ids_log_file()

    pipeline_date = "2025-01-01"
    index_date = "2025-02-02"
    job_id = "test-job-id"
    index_name = f"concepts-indexed-{index_date}"

    _mock_es_secrets()

    # Mock a file storing the date of the last index remover run
    MockSmartOpen.mock_s3_file(
        f"s3://wellcomecollection-catalogue-graph/ingestor/{pipeline_date}/{index_date}/report.index_remover.json",
        json.dumps(
            {
                "pipeline_date": pipeline_date,
                "index_date": index_date,
                "job_id": "20240103T1200",
                "deleted_count": 2,
                "date": "2025-04-07",
            }
        ),
    )

    index_concepts(
        ["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"], index_name
    )

    indexed_concepts = MockElasticsearchClient.indexed_documents[index_name]
    assert len(indexed_concepts) == 5

    event = ReporterEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        success_count=1000,
        force_pass=True,
    )
    lambda_handler([event], None)

    indexed_concepts = MockElasticsearchClient.indexed_documents[index_name]

    # Only two of the concepts in the mock file were inserted after the last run, so only those two should be removed
    # from the index
    assert len(indexed_concepts) == 3
    assert set(indexed_concepts.keys()) == {"u6jve2vb", "amzfbrbz", "someid12"}


def test_index_remover_safety_check() -> None:
    # Mock a scenario which would result in a significant percentage of IDs being deleted
    mock_deleted_ids_log_file()
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    event = ReporterEvent(
        pipeline_date=None,
        index_date=None,
        job_id=None,
        success_count=1000,
    )
    with pytest.raises(ValueError):
        lambda_handler([event], None)


def test_index_remover_no_deleted_ids_file() -> None:
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    # If the file storing deleted IDs does not exist, something went wrong and an exception should be thrown.
    event = ReporterEvent(
        pipeline_date=None,
        index_date=None,
        job_id=None,
        success_count=1000,
    )
    with pytest.raises(KeyError):
        lambda_handler([event], None)


def test_index_remover_new_index_run() -> None:
    mock_deleted_ids_log_file()

    # Mock an index which was created *after* some IDs were deleted from the graph
    pipeline_date = "2025-01-01"
    index_date = "2025-04-07"
    job_id = "test-job-id"
    index_name = f"concepts-indexed-{index_date}"

    _mock_es_secrets()

    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"], index_name)

    indexed_concepts = MockElasticsearchClient.indexed_documents[index_name]
    assert len(indexed_concepts) == 4

    event = ReporterEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        success_count=1000,
        force_pass=True,
    )

    lambda_handler([event], None)

    indexed_concepts = MockElasticsearchClient.indexed_documents[index_name]

    # Check that only IDs which were removed after the index was created get removed.
    # (In a real-life scenario, the other two IDs would not exist in the index at all.)
    assert len(indexed_concepts) == 2
    assert set(indexed_concepts.keys()) == {"u6jve2vb", "amzfbrbz"}
