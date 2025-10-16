import json
from datetime import date, datetime, timedelta

import polars as pl
import pytest

from graph_remover import IDS_LOG_SCHEMA, lambda_handler
from tests.mocks import MockRequest, MockSmartOpen
from tests.test_utils import add_mock_transformer_outputs_for_ontologies, load_fixture

REMOVER_S3_PREFIX = "s3://wellcomecollection-catalogue-graph/graph_remover"


def get_mock_remover_uri(pipeline_date: str, folder: str) -> str:
    return f"{REMOVER_S3_PREFIX}/{pipeline_date}/{folder}/loc_concepts__nodes.parquet"


def mock_deleted_ids_log_file(
    ids: list[str], pipeline_date: str, age_in_days: int
) -> None:
    past_date = datetime.today().date() - timedelta(days=age_in_days)
    mock_data = {
        "timestamp": [past_date, past_date],
        "id": ids,
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(
        get_mock_remover_uri(pipeline_date, "deleted_ids"), df
    )


def mock_neptune_response(request_data: dict, response_data: dict) -> None:
    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data=response_data,
        body=json.dumps(request_data),
    )


def mock_neptune_get_existing_response(node_ids: list) -> None:
    mock_neptune_response(
        request_data={
            "query": "MATCH (n) WHERE id(n) IN $ids RETURN id(n) AS id",
            "parameters": {"ids": node_ids},
        },
        response_data={"results": [{"id": i} for i in node_ids]},
    )


def mock_neptune_removal_response(node_ids: list) -> None:
    mock_neptune_response(
        request_data={
            "query": "MATCH (n) WHERE id(n) IN $ids DETACH DELETE n",
            "parameters": {"ids": node_ids},
        },
        response_data={"results": []},
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
        print(s3_uri, ids)
        timestamps = pl.Series(df.select(pl.col("timestamp"))).to_list()

        assert set(timestamps) == expected_timestamps
        assert set(ids) == expected_ids


def test_graph_remover_first_run() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc"])

    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    with MockSmartOpen.open(
        get_mock_remover_uri("dev", "previous_ids_snapshot"), "rb"
    ) as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 30
        assert "sh00000001" in ids


def test_graph_remover_next_run() -> None:
    pipeline_date = "2022-02-02"
    # Mock a previous concept IDs snapshot which purposefully omits some IDs from the bulk load file mock
    # (sh00000001, sh00000002, sh00000003) and includes some IDs not in the bulk load file mock
    # (sh00000004, sh00000005, sh00000006).
    MockSmartOpen.mock_s3_file(
        get_mock_remover_uri(pipeline_date, "previous_ids_snapshot"),
        load_fixture("loc/id_snapshot_loc_concepts__nodes.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(["loc"], pipeline_date=pipeline_date)

    # Mock a deleted ids snapshot which includes two of the redundant IDs (sh00000004, sh00000005), leaving
    # sh00000006 as the only ID which should be removed as part of this run.
    mock_deleted_ids_log_file(
        ["sh00000004", "sh00000005"], pipeline_date, age_in_days=364
    )
    mock_neptune_get_existing_response(["sh00000006"])
    mock_neptune_removal_response(["sh00000006"])
    mock_neptune_count_response()

    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": pipeline_date,
    }
    lambda_handler(event, None)

    # Verify that the correct IDs are listed in the 'added IDs' file
    today = datetime.today().date()
    _check_added_removed_ids(
        get_mock_remover_uri(pipeline_date, "added_ids"),
        {today},
        {"sh00000001", "sh00000002", "sh00000003"},
    )

    # Verify that the correct IDs are listed in the 'removed IDs' file
    almost_year_ago = datetime.today().date() - timedelta(days=364)
    _check_added_removed_ids(
        get_mock_remover_uri(pipeline_date, "deleted_ids"),
        {today, almost_year_ago},
        {"sh00000004", "sh00000005", "sh00000006"},
    )

    with MockSmartOpen.open(
        get_mock_remover_uri(pipeline_date, "previous_ids_snapshot"), "rb"
    ) as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert len(set(ids)) == 30
        assert "sh00000001" in ids
        assert "sh00000002" in ids
        assert "sh00000003" in ids
        assert "sh00000006" not in ids


def test_graph_remover_old_id_removal() -> None:
    MockSmartOpen.mock_s3_file(
        get_mock_remover_uri("dev", "previous_ids_snapshot"),
        load_fixture("loc/id_snapshot_loc_concepts__nodes.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(["loc"])

    # Mock a file with existing deleted IDs which are 1+ year old
    mock_deleted_ids_log_file(["sh00000004", "sh00000005"], "dev", age_in_days=365)
    mock_neptune_get_existing_response(["sh00000006"])
    mock_neptune_removal_response(["sh00000006"])
    mock_neptune_count_response()

    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    # The old deleted IDs (and corresponding timestamps) should no longer be in the file
    today = datetime.today().date()
    _check_added_removed_ids(
        get_mock_remover_uri("dev", "deleted_ids"), {today}, {"sh00000006"}
    )


def test_graph_remover_safety_check() -> None:
    # Mock a snapshot with a large number of nodes, most of which are not in the mock bulk load file.
    # This would result in a large number of deletions and should therefore trigger the safety check
    MockSmartOpen.mock_s3_file(
        get_mock_remover_uri("dev", "previous_ids_snapshot"),
        load_fixture("catalogue/id_snapshot_catalogue_concepts__nodes_large.parquet"),
    )

    add_mock_transformer_outputs_for_ontologies(["loc"])

    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    with pytest.raises(ValueError):
        lambda_handler(event, None)


def test_graph_remover_missing_bulk_load_file() -> None:
    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "2020-01-01",
    }

    with pytest.raises(KeyError):
        lambda_handler(event, None)
