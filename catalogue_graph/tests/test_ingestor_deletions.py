import polars as pl
import pydantic
import pytest

from ingestor.steps.ingestor_deletions import lambda_handler
from tests.mocks import MockElasticsearchClient, MockSmartOpen, mock_es_secrets

REMOVER_S3_PREFIX = "s3://wellcomecollection-catalogue-graph/graph_remover_incremental"

MOCK_EVENT = {
    "ingestor_type": "concepts",
    "pipeline_date": "dev",
    "index_date": "dev",
    "job_id": "dev",
}

MOCK_TIME_WINDOW_EVENT = {
    **MOCK_EVENT,
    "window": {"start_time": "2025-10-22T08:00", "end_time": "2025-10-22T08:15"},
}


def index_concepts(ids: list[str], index_name: str = "concepts-indexed-dev") -> None:
    for _id in ids:
        MockElasticsearchClient.index(index_name, _id, {})


def get_indexed_concept_ids(index_name: str = "concepts-indexed-dev") -> list[str]:
    docs = MockElasticsearchClient.indexed_documents[index_name] or {}
    return list(docs.keys())


def mock_deleted_ids_log_file(mock_ids: list[str], pipeline_date: str) -> None:
    df = pl.DataFrame(mock_ids)

    uri = f"{REMOVER_S3_PREFIX}/{pipeline_date}/deleted_ids/catalogue_concepts__nodes.parquet"
    MockSmartOpen.mock_s3_parquet_file(uri, df)


def mock_time_window_deleted_ids_log_file(
    mock_ids: list[str],
    pipeline_date: str,
) -> None:
    df = pl.DataFrame(mock_ids)

    file_name = "catalogue_concepts__nodes.parquet"
    uri = f"{REMOVER_S3_PREFIX}/{pipeline_date}/windows/20251022T0800-20251022T0815/deleted_ids/{file_name}"
    MockSmartOpen.mock_s3_parquet_file(uri, df)


def test_ingestor_deletions_no_safety_check_first_run() -> None:
    mock_es_secrets("concepts_ingestor", "dev")
    mock_deleted_ids_log_file(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"], "dev")

    # Index some empty documents with the same IDs as those stored in the parquet mock
    # (plus an extra document which shouldn't be removed).
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])
    assert len(get_indexed_concept_ids()) == 5

    # No index date specified, so the local 'concepts-indexed-dev' index name should be used
    event = {**MOCK_EVENT, "force_pass": True}
    lambda_handler(event, None)

    indexed_concepts = get_indexed_concept_ids()
    assert indexed_concepts == ["someid12"]


def test_ingestor_deletions_incremental_mode() -> None:
    mock_es_secrets("concepts_ingestor", "dev")

    # Mock two sets of deleted IDs, one with a time window and one without
    mock_deleted_ids_log_file(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"], "dev")
    mock_time_window_deleted_ids_log_file(["u6jve2vb", "amzfbrbz"], "dev")

    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])
    assert len(get_indexed_concept_ids()) == 5

    event = {**MOCK_TIME_WINDOW_EVENT, "force_pass": True}
    lambda_handler(event, None)

    # Only the documents listed in the time window event should be removed
    indexed_concepts = get_indexed_concept_ids()
    assert set(indexed_concepts) == {"q5a7uqkz", "s8f6cxcf", "someid12"}


def test_ingestor_deletions_empty_ids_file() -> None:
    mock_es_secrets("concepts_ingestor", "dev")

    # Mock an empty dataframe
    mock_deleted_ids_log_file([], "dev")

    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])
    assert len(get_indexed_concept_ids()) == 5

    lambda_handler(MOCK_EVENT, None)

    # No concept IDs should be removed
    assert len(get_indexed_concept_ids()) == 5


def test_ingestor_deletions_line_safety_check() -> None:
    # Mock a scenario which would result in a significant percentage of IDs being deleted
    mock_deleted_ids_log_file(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"], "dev")
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    with pytest.raises(ValueError, match="Fractional change"):
        lambda_handler(MOCK_EVENT, None)


def test_ingestor_deletions_line_no_deleted_ids_file() -> None:
    index_concepts(["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf", "someid12"])

    # If the file storing deleted IDs does not exist, something went wrong and an exception should be thrown.
    with pytest.raises(KeyError):
        lambda_handler(MOCK_EVENT, None)


def test_ingestor_deletions_works() -> None:
    # Removing works should be impossible
    event = {**MOCK_EVENT, "ingestor_type": "works"}

    with pytest.raises(pydantic.ValidationError):
        lambda_handler(event, None)
