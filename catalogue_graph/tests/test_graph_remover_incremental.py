from typing import Any

import polars as pl
import pydantic
import pytest
from freezegun import freeze_time

from graph_remover_incremental import lambda_handler
from tests.mocks import (
    MockCloudwatchClient,
    MockSmartOpen,
    add_neptune_mock_response,
)
from tests.test_utils import add_mock_merged_documents

REMOVER_S3_PREFIX = "s3://wellcomecollection-catalogue-graph/graph_remover_incremental"


def mock_neptune_get_disconnected_concept_nodes(node_ids: list) -> None:
    add_neptune_mock_response(
        expected_query="MATCH (n: Concept) WHERE NOT (n)-[:HAS_CONCEPT]-() RETURN id(n) AS id",
        expected_params={},
        mock_results=[{"id": i} for i in node_ids],
    )


def mock_neptune_get_total_node_count(label: str, count: int) -> None:
    add_neptune_mock_response(
        expected_query=f"MATCH (n: {label}) RETURN count(n) AS count",
        expected_params=None,
        mock_results=[{"count": count}],
    )


def mock_neptune_get_total_edge_count(label: str, count: int) -> None:
    add_neptune_mock_response(
        expected_query=f"MATCH ()-[e:{label}]->() RETURN count(e) AS count",
        expected_params=None,
        mock_results=[{"count": count}],
    )


def mock_neptune_get_existing_nodes_response(node_ids: list) -> None:
    add_neptune_mock_response(
        expected_query="MATCH (n) WHERE id(n) IN $ids RETURN id(n) AS id",
        expected_params={"ids": node_ids},
        mock_results=[{"id": i} for i in node_ids],
    )


def mock_neptune_get_existing_edges_response(edge_ids: list) -> None:
    add_neptune_mock_response(
        expected_query="MATCH ()-[e]->() WHERE id(e) IN $ids RETURN id(e) AS id",
        expected_params={"ids": edge_ids},
        mock_results=[{"id": i} for i in edge_ids],
    )


def mock_neptune_delete_nodes_response(node_ids: list[str]) -> None:
    add_neptune_mock_response(
        expected_query="MATCH (n) WHERE id(n) IN $ids DETACH DELETE n",
        expected_params={"ids": node_ids},
        mock_results=[],
    )


def mock_neptune_delete_edges_response(edge_ids: list[str]) -> None:
    add_neptune_mock_response(
        expected_query="MATCH ()-[e]->() WHERE id(e) IN $ids DELETE e",
        expected_params={"ids": edge_ids},
        mock_results=[],
    )


def mock_neptune_get_edges_response(node_ids: list[str], results: list[dict]) -> None:
    add_neptune_mock_response(
        expected_query="""UNWIND $ids AS id
            MATCH (n {`~id`: id})-[e:HAS_CONCEPT]-()
            RETURN id(n) AS id, collect(id(e)) AS edge_ids
        """,
        expected_params={"ids": node_ids},
        mock_results=results,
    )


def check_deleted_ids_log(s3_uri: str, expected_ids: set[str]) -> None:
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list()
        assert set(ids) == expected_ids


def test_graph_remover_incremental_concept_nodes() -> None:
    disconnected_ids = ["byzuqyr5", "vjfb76xy"]
    mock_neptune_get_total_node_count("Concept", 100)
    mock_neptune_get_disconnected_concept_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(disconnected_ids)
    mock_neptune_delete_nodes_response(disconnected_ids)

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    s3_uri = f"{REMOVER_S3_PREFIX}/dev/deleted_ids/catalogue_concepts__nodes.parquet"
    check_deleted_ids_log(s3_uri, set(disconnected_ids))


def test_graph_remover_incremental_concept_edges() -> None:
    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "edges",
        "pipeline_date": "2024-06-06",
    }
    lambda_handler(event, None)

    s3_uri = (
        f"{REMOVER_S3_PREFIX}/2024-06-06/deleted_ids/catalogue_concepts__edges.parquet"
    )
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        # There are no concept edges to remove
        assert len(df) == 0


def test_graph_remover_incremental_work_edges() -> None:
    # Add three visible works to the merged index.
    add_mock_merged_documents("2024-06-06", work_status="Visible")
    mock_neptune_get_total_edge_count("HAS_CONCEPT", 12345)

    # Mock HAS_CONCEPT graph relationships for all three works, some of which also exist in the merged index,
    # and some of which only exist in the graph (and should be removed).
    neptune_edges = [
        {
            "id": "f33w7jru",
            "edge_ids": [
                "HAS_CONCEPT:f33w7jru-->kpeywdvq",
                "HAS_CONCEPT:f33w7jru-->vykuavkt",
                "HAS_CONCEPT:f33w7jru-->s6s24vd9",
                "HAS_CONCEPT:f33w7jru-->123",  # should be removed
                "HAS_CONCEPT:f33w7jru-->456",  # should be removed
            ],
        },
        {
            "id": "m4u8drnu",
            "edge_ids": [
                "HAS_CONCEPT:m4u8drnu-->789",  # should be removed
            ],
        },
        {
            "id": "ydz8wd5r",
            "edge_ids": ["HAS_CONCEPT:ydz8wd5r-->yfqryj26"],
        },
    ]

    mock_neptune_get_edges_response(
        ["f33w7jru", "m4u8drnu", "ydz8wd5r"], results=neptune_edges
    )

    edges_to_remove = [
        "HAS_CONCEPT:f33w7jru-->123",
        "HAS_CONCEPT:f33w7jru-->456",
        "HAS_CONCEPT:m4u8drnu-->789",
    ]
    mock_neptune_get_existing_edges_response(edges_to_remove)
    mock_neptune_delete_edges_response(edges_to_remove)

    event = {
        "transformer_type": "catalogue_works",
        "entity_type": "edges",
        "pipeline_date": "2024-06-06",
    }
    lambda_handler(event, None)

    s3_uri = (
        f"{REMOVER_S3_PREFIX}/2024-06-06/deleted_ids/catalogue_works__edges.parquet"
    )
    check_deleted_ids_log(s3_uri, set(edges_to_remove))


def test_graph_remover_incremental_work_nodes() -> None:
    # Add one invisible work to the merged index
    add_mock_merged_documents("dev", work_status="Invisible")
    mock_neptune_get_existing_nodes_response(["sghsneca"])
    mock_neptune_delete_nodes_response(["sghsneca"])
    mock_neptune_get_total_node_count("Work", 100)

    event = {
        "transformer_type": "catalogue_works",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }
    lambda_handler(event, None)

    s3_uri = f"{REMOVER_S3_PREFIX}/dev/deleted_ids/catalogue_works__nodes.parquet"
    check_deleted_ids_log(s3_uri, {"sghsneca"})


def test_graph_remover_catalogue_failure() -> None:
    # LoC concepts can only be removed using the full graph remover
    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }

    with pytest.raises(pydantic.ValidationError):
        lambda_handler(event, None)


def test_graph_remover_safety_mechanism() -> None:
    disconnected_ids = ["byzuqyr5", "vjfb76xy"]
    mock_neptune_get_total_node_count("Concept", 9)
    mock_neptune_get_disconnected_concept_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(disconnected_ids)
    mock_neptune_delete_nodes_response(disconnected_ids)

    event: dict[str, Any] = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
    }

    # Safety check enabled
    with pytest.raises(
        ValueError, match="Fractional change 0.22 exceeds threshold 0.2!"
    ):
        lambda_handler(event, None)

    # Safety check disabled
    event["force_pass"] = True
    lambda_handler(event, None)
    s3_uri = f"{REMOVER_S3_PREFIX}/dev/deleted_ids/catalogue_concepts__nodes.parquet"
    check_deleted_ids_log(s3_uri, set(disconnected_ids))


@freeze_time("2025-02-10")
def test_metrics() -> None:
    disconnected_ids = ["byzuqyr5", "vjfb76xy"]
    mock_neptune_get_total_node_count("Concept", 100)
    mock_neptune_get_disconnected_concept_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(disconnected_ids)
    mock_neptune_delete_nodes_response(disconnected_ids)

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "window": {"end_time": "2025-02-02T12:00"},
    }
    lambda_handler(event, None)

    assert MockCloudwatchClient.metrics_reported == [
        {
            "dimensions": {
                "entity_type": "nodes",
                "pipeline_date": "dev",
                "transformer_type": "catalogue_concepts",
            },
            "metric_name": "deleted_count",
            "namespace": "catalogue_graph_pipeline",
            "value": 2,
        }
    ]
