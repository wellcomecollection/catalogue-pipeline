from datetime import datetime

import polars as pl
import pytest
from test_mocks import MockRequest, MockSmartOpen
from test_utils import load_fixture

from graph_remover import lambda_handler

CATALOGUE_CONCEPTS_SNAPSHOT_URI = "s3://wellcomecollection-catalogue-graph/graph_remover/previous_ids_snapshot/catalogue_concepts__nodes.parquet"
CATALOGUE_CONCEPTS_REMOVED_IDS_URI = "s3://wellcomecollection-catalogue-graph/graph_remover/deleted_ids/catalogue_concepts__nodes.parquet"
CATALOGUE_CONCEPTS_ADDED_IDS_URI = "s3://wellcomecollection-catalogue-graph/graph_remover/added_ids/catalogue_concepts__nodes.parquet"


def add_bulk_load_file_mocks() -> None:
    for file_name in ["catalogue_concepts__nodes", "catalogue_works__nodes"]:
        MockSmartOpen.mock_s3_file(
            f"s3://wellcomecollection-neptune-graph-loader/{file_name}.csv",
            load_fixture(f"catalogue/{file_name}.csv").decode(),
        )


def test_graph_remover_first_run() -> None:
    add_bulk_load_file_mocks()

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
    lambda_handler(event, None)

    with MockSmartOpen.open(CATALOGUE_CONCEPTS_SNAPSHOT_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 19
        assert "vjfb76xy" in ids


def test_graph_remover_next_run() -> None:
    # Mock a previous concept IDs snapshot which purposefully omits some IDs from the bulk load file mock
    # and includes some IDs not in the bulk load file mock.
    MockSmartOpen.mock_s3_file(
        CATALOGUE_CONCEPTS_SNAPSHOT_URI,
        load_fixture("catalogue/id_snapshot_catalogue_concepts__nodes.parquet"),
    )

    add_bulk_load_file_mocks()

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

    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}
    lambda_handler(event, None)

    # Verify that the correct IDs are listed in the 'added IDs' file
    with MockSmartOpen.open(CATALOGUE_CONCEPTS_ADDED_IDS_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.col("id"))).to_list()

        timestamps = pl.Series(df.select(pl.col("timestamp"))).to_list()
        assert len(set(timestamps)) == 1
        assert timestamps[0] == datetime.today().date()

        assert set(ids) == {"fqe7m83w", "pnpsyqp8", "drypfe3u"}

    # Verify that the correct IDs are listed in the 'removed IDs' file
    with MockSmartOpen.open(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.col("id"))).to_list()

        timestamps = pl.Series(df.select(pl.col("timestamp"))).to_list()
        assert len(set(timestamps)) == 1
        assert timestamps[0] == datetime.today().date()

        assert set(ids) == {"byzuqyr5"}

    # Verify that the snapshot file was correctly updated
    with MockSmartOpen.open(CATALOGUE_CONCEPTS_SNAPSHOT_URI, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 19
        assert "vjfb76xy" in ids
        assert "byzuqyr5" not in ids
        assert "fqe7m83w" in ids
        assert "drypfe3u" in ids


def test_graph_remover_missing_bulk_load_file() -> None:
    event = {"transformer_type": "catalogue_concepts", "entity_type": "nodes"}

    with pytest.raises(KeyError):
        lambda_handler(event, None)
