from datetime import date, datetime, timedelta

import polars as pl
import pytest
from test_mocks import MockRequest, MockSmartOpen
from test_utils import load_fixture

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


def add_bulk_load_file_mocks() -> None:
    for file_name in ["catalogue_concepts__nodes", "catalogue_works__nodes"]:
        MockSmartOpen.mock_s3_file(
            f"s3://wellcomecollection-neptune-graph-loader/{file_name}.csv",
            load_fixture(f"catalogue/{file_name}.csv").decode(),
        )


def mock_deleted_ids_log_file(age_in_days: int) -> None:
    past_date = datetime.today().date() - timedelta(days=age_in_days)
    mock_data = {
        "timestamp": [past_date, past_date],
        "id": ["u6jve2vb", "amzfbrbz"],
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, df)


def mock_neptune_removal_response() -> None:
    MockRequest.mock_responses(
        [
            {
                "method": "POST",
                "url": "https://test-host.com:8182/openCypher",
                "status_code": 200,
                "json_data": {"results": [{"deletedCount": 1}]},
                "content_bytes": None,
                "params": None,
            }
        ]
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
    add_bulk_load_file_mocks()

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
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

    add_bulk_load_file_mocks()
    mock_deleted_ids_log_file(age_in_days=364)
    mock_neptune_removal_response()

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
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

    add_bulk_load_file_mocks()

    # Mock a file with existing deleted IDs which are 1+ year old
    mock_deleted_ids_log_file(age_in_days=365)
    mock_neptune_removal_response()

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
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

    add_bulk_load_file_mocks()

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
    with pytest.raises(ValueError):
        lambda_handler(event, None)


def test_graph_remover_missing_bulk_load_file() -> None:
    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}

    with pytest.raises(KeyError):
        lambda_handler(event, None)
