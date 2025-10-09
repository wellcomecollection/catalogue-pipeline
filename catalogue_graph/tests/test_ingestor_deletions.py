import polars as pl
import pytest
from test_graph_remover import CATALOGUE_CONCEPTS_REMOVED_IDS_URI
from test_mocks import MockElasticsearchClient, MockSmartOpen, mock_es_secrets

from graph_remover import IDS_LOG_SCHEMA
from ingestor.steps.ingestor_deletions import lambda_handler

MOCK_EVENT = {
    "ingestor_type": "concepts",
    "pipeline_date": "dev",
    "index_date": "dev",
    "job_id": "dev",
}


def index_concepts(ids: list[str], index_name: str = "concepts-indexed-dev") -> None:
    for _id in ids:
        MockElasticsearchClient.index(index_name, _id, {})


def get_indexed_concepts(index_name: str = "concepts-indexed-dev") -> dict:
    return MockElasticsearchClient.indexed_documents[index_name] or {}


def mock_deleted_ids_log_file() -> None:
    mock_data = {
        "timestamp": ["2025-04-03", "2025-04-03", "2025-04-07", "2025-04-07"],
        "id": ["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"],
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, df)


def test_ingestor_deletions_linetest_ingestor_deletions_line_safety_check_first_run() -> (
    None
):
    mock_es_secrets("concepts_ingestor", "dev")
    mock_deleted_ids_log_file()

    # Index some empty documents with the same IDs as those stored in the parquet mock
    # (plus an extra document which shouldn't be removed).
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])
    assert len(get_indexed_concepts()) == 5

    # No index date specified, so the local 'concepts-indexed-dev' index name should be used
    event = {**MOCK_EVENT, "force_pass": True}
    lambda_handler(event, None)

    indexed_concepts = get_indexed_concepts()
    assert len(indexed_concepts) == 1
    assert list(indexed_concepts.keys())[0] == "someid12"


def test_ingestor_deletions_line_safety_check() -> None:
    # Mock a scenario which would result in a significant percentage of IDs being deleted
    mock_deleted_ids_log_file()
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    with pytest.raises(ValueError):
        lambda_handler(MOCK_EVENT, None)


def test_ingestor_deletions_line_no_deleted_ids_file() -> None:
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    # If the file storing deleted IDs does not exist, something went wrong and an exception should be thrown.
    with pytest.raises(KeyError):
        lambda_handler(MOCK_EVENT, None)


def test_ingestor_deletions_line_new_index_run() -> None:
    mock_deleted_ids_log_file()

    # Mock an index which was created *after* some IDs were deleted from the graph
    pipeline_date = "2025-01-01"
    index_date = "2025-04-07"
    job_id = "test-job-id"
    index_name = f"concepts-indexed-{index_date}"

    mock_es_secrets("concepts_ingestor", pipeline_date)

    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"], index_name)

    assert len(get_indexed_concepts(index_name)) == 4

    event = {
        "ingestor_type": "concepts",
        "pipeline_date": pipeline_date,
        "index_date": index_date,
        "job_id": job_id,
        "force_pass": True,
    }
    lambda_handler(event, None)

    indexed_concepts = get_indexed_concepts(index_name)

    # Check that only IDs which were removed after the index was created get removed.
    # (In a real-life scenario, the other two IDs would not exist in the index at all.)
    assert len(indexed_concepts) == 2
    assert set(indexed_concepts.keys()) == {"u6jve2vb", "amzfbrbz"}
