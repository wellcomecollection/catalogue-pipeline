import json
from datetime import date, datetime, timedelta

import polars as pl
import pytest
from test_mocks import MockRequest, MockSmartOpen
from test_utils import add_mock_transformer_outputs_for_ontologies, load_fixture

from graph_remover import IDS_LOG_SCHEMA, lambda_handler

REMOVER_S3_PREFIX = "s3://wellcomecollection-catalogue-graph/graph_remover"
CATALOGUE_CONCEPTS_SNAPSHOT_URI = (
    f"{REMOVER_S3_PREFIX}/previous_ids_snapshot/catalogue_concepts__nodes.parquet"
)
CATALOGUE_CONCEPTS_REMOVED_IDS_URI = (
    f"{REMOVER_S3_PREFIX}/deleted_ids/catalogue_concepts__nodes.parquet"
)
CATALOGUE_CONCEPTS_ADDED_IDS_URI = (
    f"{REMOVER_S3_PREFIX}/added_ids/catalogue_concepts__nodes.parquet"
)


def mock_deleted_ids_log_file(age_in_days: int) -> None:
    past_date = datetime.today().date() - timedelta(days=age_in_days)
    mock_data = {
        "timestamp": [past_date, past_date],
        "id": ["u6jve2vb", "amzfbrbz"],
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, df)


def mock_neptune_response(request_data: dict, response_data: dict) -> None:
    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data=response_data,
        body=json.dumps(request_data),
    )


def mock_neptune_removal_response(node_ids: list) -> None:
    mock_neptune_response(
        request_data={
            "query": "MATCH (n) WHERE id(n) IN $nodeIds DETACH DELETE n",
            "parameters": {"nodeIds": node_ids},
        },
        response_data={"results": [{"deletedCount": 1}]},
    )


def mock_neptune_count_response() -> None:
    mock_neptune_response(
        request_data={"query": "MATCH (n) RETURN count(n) AS nodeCount"},
        response_data={"results": [{"nodeCount": 1}]},
    )


def _check_added_removed_ids(
    s3_uri: str, expected_timestamps: set[date], expected_ids: set[str]
) -> None:
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.col("id"))).to_list()
        timestamps = pl.Series(df.select(pl.col("timestamp"))).to_list()

        assert set(timestamps) == expected_timestamps
        assert set(ids) == expected_ids


def test_graph_remover_first_run() -> None:
    add_mock_transformer_outputs_for_ontologies(["catalogue"])

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    with MockSmartOpen.open(CATALOGUE_CONCEPTS_SNAPSHOT_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 23
        assert "vjfb76xy" in ids


def test_graph_remover_next_run() -> None:
    # Mock a previous concept IDs snapshot which purposefully omits some IDs from the bulk load file mock
    # and includes some IDs not in the bulk load file mock.
    MockSmartOpen.mock_s3_file(
        CATALOGUE_CONCEPTS_SNAPSHOT_URI,
        load_fixture("catalogue/id_snapshot_catalogue_concepts__nodes.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(
        ["catalogue"], pipeline_date="2022-02-02"
    )
    mock_deleted_ids_log_file(age_in_days=364)
    mock_neptune_removal_response(["byzuqyr5"])
    mock_neptune_count_response()

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "2022-02-02",
    }
    lambda_handler(event, None)

    # Verify that the correct IDs are listed in the 'added IDs' file
    today = datetime.today().date()
    _check_added_removed_ids(
        CATALOGUE_CONCEPTS_ADDED_IDS_URI,
        {today},
        {"fqe7m83w", "pnpsyqp8", "drypfe3u"},
    )

    # Verify that the correct IDs are listed in the 'removed IDs' file
    almost_year_ago = datetime.today().date() - timedelta(days=364)
    _check_added_removed_ids(
        CATALOGUE_CONCEPTS_REMOVED_IDS_URI,
        {today, almost_year_ago},
        {"u6jve2vb", "amzfbrbz", "byzuqyr5"},
    )

    with MockSmartOpen.open(CATALOGUE_CONCEPTS_SNAPSHOT_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 23
        assert "vjfb76xy" in ids
        assert "byzuqyr5" not in ids
        assert "fqe7m83w" in ids
        assert "drypfe3u" in ids


def test_graph_remover_old_id_removal() -> None:
    MockSmartOpen.mock_s3_file(
        CATALOGUE_CONCEPTS_SNAPSHOT_URI,
        load_fixture("catalogue/id_snapshot_catalogue_concepts__nodes.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(["catalogue"])

    # Mock a file with existing deleted IDs which are 1+ year old
    mock_deleted_ids_log_file(age_in_days=365)
    mock_neptune_removal_response(["byzuqyr5"])
    mock_neptune_count_response()

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    # The old deleted IDs (and corresponding timestamps) should no longer be in the file
    today = datetime.today().date()
    _check_added_removed_ids(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, {today}, {"byzuqyr5"})


def test_graph_remover_safety_check() -> None:
    # Mock a snapshot with a large number of nodes, most of which are not in the mock bulk load file.
    # This would result in a large number of deletions and should therefore trigger the safety check
    MockSmartOpen.mock_s3_file(
        CATALOGUE_CONCEPTS_SNAPSHOT_URI,
        load_fixture("catalogue/id_snapshot_catalogue_concepts__nodes_large.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(["catalogue"])

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    with pytest.raises(ValueError):
        lambda_handler(event, None)


def test_graph_remover_missing_bulk_load_file() -> None:
    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "2020-01-01",
    }

    with pytest.raises(KeyError):
        lambda_handler(event, None)
