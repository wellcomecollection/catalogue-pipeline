from typing import Any

import polars as pl
import pydantic
import pytest
from freezegun import freeze_time

import graph.removers.catalogue_work_identifiers_remover as remover_module
from graph.steps.graph_remover_incremental import lambda_handler
from tests.mocks import (
    MockCloudwatchClient,
    MockElasticsearchClient,
    MockSmartOpen,
    add_neptune_mock_response,
    mock_es_secrets,
    mock_neptune_secrets,
)
from tests.test_utils import add_mock_merged_documents

BUCKET = "wellcomecollection-catalogue-graph"


def get_remover_s3_uri(graph_date: str, pipeline_date: str, suffix: str) -> str:
    return f"s3://{BUCKET}/graph-{graph_date}/pipeline-{pipeline_date}/graph_remover_incremental/{suffix}"


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


def mock_neptune_get_edges_response(
    node_ids: list[str], results: list[dict], edge_label: str = "HAS_CONCEPT"
) -> None:
    add_neptune_mock_response(
        expected_query=f"""UNWIND $ids AS id
            MATCH (n {{`~id`: id}})-[e:{edge_label}]-()
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
    mock_neptune_secrets("dev")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "graph_date": "dev",
    }
    lambda_handler(event, None)

    s3_uri = get_remover_s3_uri(
        "dev", "dev", "full/deleted_ids/catalogue_concepts__nodes.parquet"
    )
    check_deleted_ids_log(s3_uri, set(disconnected_ids))


def test_graph_remover_incremental_concept_edges() -> None:
    mock_neptune_secrets("2024-06-06")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="2024-06-06")
    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "edges",
        "pipeline_date": "2024-06-06",
        "graph_date": "2024-06-06",
    }
    lambda_handler(event, None)

    s3_uri = get_remover_s3_uri(
        "2024-06-06", "2024-06-06", "full/deleted_ids/catalogue_concepts__edges.parquet"
    )
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        # There are no concept edges to remove
        assert len(df) == 0


def test_graph_remover_incremental_work_edges() -> None:
    # Add three visible works to the merged index.
    add_mock_merged_documents("2024-06-06", work_status="Visible")
    mock_neptune_get_total_edge_count("HAS_CONCEPT", 12345)
    mock_neptune_secrets("2024-06-06")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="2024-06-06")

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
        "graph_date": "2024-06-06",
    }
    lambda_handler(event, None)

    s3_uri = get_remover_s3_uri(
        "2024-06-06", "2024-06-06", "full/deleted_ids/catalogue_works__edges.parquet"
    )
    check_deleted_ids_log(s3_uri, set(edges_to_remove))


def test_graph_remover_incremental_work_nodes() -> None:
    # Add one invisible work to the merged index
    add_mock_merged_documents("dev", work_status="Invisible")
    mock_neptune_get_existing_nodes_response(["sghsneca"])
    mock_neptune_delete_nodes_response(["sghsneca"])
    mock_neptune_get_total_node_count("Work", 100)
    mock_neptune_secrets("dev")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")

    event = {
        "transformer_type": "catalogue_works",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "graph_date": "dev",
    }
    lambda_handler(event, None)

    s3_uri = get_remover_s3_uri(
        "dev", "dev", "full/deleted_ids/catalogue_works__nodes.parquet"
    )
    check_deleted_ids_log(s3_uri, {"sghsneca"})


def test_graph_remover_catalogue_failure() -> None:
    # LoC concepts can only be removed using the full graph remover
    event = {
        "transformer_type": "loc_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "graph_date": "dev",
    }

    with pytest.raises(pydantic.ValidationError):
        lambda_handler(event, None)


def test_graph_remover_safety_mechanism() -> None:
    disconnected_ids = ["byzuqyr5", "vjfb76xy"]
    mock_neptune_get_total_node_count("Concept", 9)
    mock_neptune_get_disconnected_concept_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(disconnected_ids)
    mock_neptune_delete_nodes_response(disconnected_ids)
    mock_neptune_secrets("dev")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")

    event: dict[str, Any] = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "graph_date": "dev",
    }

    # Safety check enabled
    with pytest.raises(
        ValueError, match="Fractional change 0.22 exceeds threshold 0.2!"
    ):
        lambda_handler(event, None)

    # Safety check disabled
    event["force_pass"] = True
    lambda_handler(event, None)
    s3_uri = get_remover_s3_uri(
        "dev", "dev", "full/deleted_ids/catalogue_concepts__nodes.parquet"
    )
    check_deleted_ids_log(s3_uri, set(disconnected_ids))


@freeze_time("2025-02-10")
def test_metrics() -> None:
    disconnected_ids = ["byzuqyr5", "vjfb76xy"]
    mock_neptune_get_total_node_count("Concept", 100)
    mock_neptune_get_disconnected_concept_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(disconnected_ids)
    mock_neptune_delete_nodes_response(disconnected_ids)
    mock_neptune_secrets("2026-06-06")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")

    event = {
        "transformer_type": "catalogue_concepts",
        "entity_type": "nodes",
        "pipeline_date": "dev",
        "graph_date": "2026-06-06",
        "window": {"end_time": "2025-02-02T12:00"},
    }
    lambda_handler(event, None)

    assert MockCloudwatchClient.metrics_reported == [
        {
            "dimensions": {
                "entity_type": "nodes",
                "pipeline_date": "dev",
                "graph_date": "2026-06-06",
                "transformer_type": "catalogue_concepts",
                "pipeline_step": "incremental_graph_remover",
            },
            "metric_name": "deleted_count",
            "namespace": "catalogue_graph_pipeline",
            "value": 2,
        }
    ]


def test_graph_remover_incremental_id_mode() -> None:
    """ID mode should scope the ES query and write output to an IDs-specific S3 path."""
    graph_date = "2025-01-01"
    pipeline_date = "dev"

    add_mock_merged_documents(pipeline_date, work_status="Invisible")
    mock_neptune_get_existing_nodes_response(["sghsneca"])
    mock_neptune_delete_nodes_response(["sghsneca"])
    mock_neptune_get_total_node_count("Work", 100)
    mock_neptune_secrets(graph_date)
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")

    event = {
        "transformer_type": "catalogue_works",
        "entity_type": "nodes",
        "pipeline_date": pipeline_date,
        "graph_date": graph_date,
        "ids": ["sghsneca"],
    }
    lambda_handler(event, None)

    s3_uri = get_remover_s3_uri(
        graph_date,
        pipeline_date,
        "by_id/sghsneca/deleted_ids/catalogue_works__nodes.parquet",
    )
    check_deleted_ids_log(s3_uri, {"sghsneca"})

    # ID mode should produce an ids query filter
    assert len(MockElasticsearchClient.queries) >= 1
    assert MockElasticsearchClient.queries[0] == {
        "bool": {
            "must": [
                {"bool": {"must_not": {"match": {"type": "Visible"}}}},
                {"ids": {"values": ["sghsneca"]}},
            ]
        }
    }


WORK_IDENTIFIERS_WINDOW = {"end_time": "2025-01-01T12:00"}
IN_WINDOW = "2025-01-01T11:50:00Z"
OUTSIDE_WINDOW = "2024-12-01T00:00:00Z"


def add_path_work(
    work_id: str, path: str, identifier: str, merged_time: str | None = IN_WINDOW
) -> None:
    MockElasticsearchClient.index(
        "works-denormalised-dev",
        work_id,
        {
            "type": "Visible",
            "state": {
                "canonicalId": work_id,
                "sourceIdentifier": {
                    "identifierType": {"id": "axiell-priref"},
                    "ontologyType": "Work",
                    "value": f"priref-{work_id}",
                },
                **({"mergedTime": merged_time} if merged_time else {}),
            },
            "data": {
                "collectionPath": {"path": path},
                "otherIdentifiers": [
                    {
                        "identifierType": {"id": "calm-altref-no"},
                        "ontologyType": "Work",
                        "value": identifier,
                    }
                ],
            },
        },
    )


def mock_neptune_get_parent_edges_response(
    node_ids: list[str], results: list[dict]
) -> None:
    add_neptune_mock_response(
        expected_query="""UNWIND $ids AS id
            MATCH (n:PathIdentifier {`~id`: id})-[e:HAS_PARENT]->()
            RETURN id(n) AS id, collect(id(e)) AS edge_ids
        """,
        expected_params={"ids": node_ids},
        mock_results=results,
    )


def mock_neptune_get_path_identifier_works_response(
    node_ids: list[str], results: list[dict]
) -> None:
    add_neptune_mock_response(
        expected_query="""UNWIND $ids AS id
            MATCH (s:Work)-[:HAS_PATH_IDENTIFIER]->(n:PathIdentifier {`~id`: id})
            RETURN id(n) AS id, collect(id(s)) AS source_ids
        """,
        expected_params={"ids": node_ids},
        mock_results=results,
    )


def mock_neptune_get_path_identifier_parent_edge_count(count: int) -> None:
    add_neptune_mock_response(
        expected_query="MATCH (:PathIdentifier)-[e:HAS_PARENT]->() RETURN count(e) AS count",
        expected_params=None,
        mock_results=[{"count": count}],
    )


def mock_work_identifiers_edge_removal(
    edges_to_remove: list[str],
    has_path_identifier_count: int = 1000,
    has_parent_count: int = 1000,
) -> None:
    mock_neptune_get_total_edge_count("HAS_PATH_IDENTIFIER", has_path_identifier_count)
    mock_neptune_get_path_identifier_parent_edge_count(has_parent_count)
    mock_neptune_get_existing_edges_response(sorted(edges_to_remove))
    mock_neptune_delete_edges_response(sorted(edges_to_remove))
    mock_neptune_secrets("dev")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")


def run_work_identifiers_edge_remover(force_pass: bool = False) -> None:
    MockElasticsearchClient.apply_range_filters = True
    lambda_handler(
        {
            "transformer_type": "catalogue_work_identifiers",
            "entity_type": "edges",
            "pipeline_date": "dev",
            "graph_date": "dev",
            "window": WORK_IDENTIFIERS_WINDOW,
            "force_pass": force_pass,
        },
        None,
    )


def check_work_identifiers_deleted_edges(expected_ids: set[str]) -> None:
    s3_uri = get_remover_s3_uri(
        "dev",
        "dev",
        "windows/20250101T1145-20250101T1200/deleted_ids/catalogue_work_identifiers__edges.parquet",
    )
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list() if len(df.columns) else []
        assert set(ids) == expected_ids


def test_work_identifiers_moved_record_removes_stale_parent_edge() -> None:
    add_path_work("moved001", "axiell:NEW/axiell:A", "A")
    add_path_work("rootwork", "axiell:B", "B")

    mock_neptune_get_edges_response(
        ["moved001", "rootwork"],
        edge_label="HAS_PATH_IDENTIFIER",
        results=[
            {
                "id": "moved001",
                "edge_ids": [
                    "HAS_PATH_IDENTIFIER:moved001-->axiell:A",
                    "HAS_PATH_IDENTIFIER:moved001-->axiell:A-OLD",
                ],
            },
            {"id": "rootwork", "edge_ids": ["HAS_PATH_IDENTIFIER:rootwork-->axiell:B"]},
        ],
    )
    mock_neptune_get_parent_edges_response(
        ["axiell:A", "axiell:B"],
        results=[
            {
                "id": "axiell:A",
                "edge_ids": [
                    "HAS_PARENT:axiell:A-->axiell:NEW",
                    "HAS_PARENT:axiell:A-->axiell:OLD",
                ],
            },
            # A record with no 982 is a root, so any parent edge it has is stale
            {"id": "axiell:B", "edge_ids": ["HAS_PARENT:axiell:B-->axiell:X"]},
        ],
    )
    mock_neptune_get_path_identifier_works_response(
        ["axiell:A", "axiell:B"],
        results=[
            {"id": "axiell:A", "source_ids": ["moved001"]},
            {"id": "axiell:B", "source_ids": ["rootwork"]},
        ],
    )
    edges_to_remove = [
        "HAS_PATH_IDENTIFIER:moved001-->axiell:A-OLD",
        "HAS_PARENT:axiell:A-->axiell:OLD",
        "HAS_PARENT:axiell:B-->axiell:X",
    ]
    mock_work_identifiers_edge_removal(edges_to_remove)

    run_work_identifiers_edge_remover()

    check_work_identifiers_deleted_edges(set(edges_to_remove))


def test_work_identifiers_window_excludes_works_without_merged_time() -> None:
    # An Elasticsearch range query never matches a document missing the field
    add_path_work("refno001", "PP/ABC", "PP/ABC")
    add_path_work("notime01", "PP/XYZ", "PP/XYZ", merged_time=None)

    mock_neptune_get_edges_response(
        ["refno001"],
        edge_label="HAS_PATH_IDENTIFIER",
        results=[
            {"id": "refno001", "edge_ids": ["HAS_PATH_IDENTIFIER:refno001-->PP/ABC"]}
        ],
    )
    mock_neptune_get_parent_edges_response(
        ["PP/ABC"], results=[{"id": "PP/ABC", "edge_ids": ["HAS_PARENT:PP/ABC-->PP"]}]
    )
    mock_work_identifiers_edge_removal([])

    run_work_identifiers_edge_remover()

    check_work_identifiers_deleted_edges(set())


def test_work_identifiers_unchanged_records_remove_nothing() -> None:
    # RefNo mode: each path matches the work's RefNo, so its parent is the path minus the last fragment
    add_path_work("refno001", "PP/ABC", "PP/ABC")
    add_path_work("refno002", "PP/ABC/1", "PP/ABC/1")

    mock_neptune_get_edges_response(
        ["refno001", "refno002"],
        edge_label="HAS_PATH_IDENTIFIER",
        results=[
            {"id": "refno001", "edge_ids": ["HAS_PATH_IDENTIFIER:refno001-->PP/ABC"]},
            {
                "id": "refno002",
                "edge_ids": ["HAS_PATH_IDENTIFIER:refno002-->PP/ABC/1"],
            },
        ],
    )
    mock_neptune_get_parent_edges_response(
        ["PP/ABC", "PP/ABC/1"],
        results=[
            {"id": "PP/ABC", "edge_ids": ["HAS_PARENT:PP/ABC-->PP"]},
            {"id": "PP/ABC/1", "edge_ids": ["HAS_PARENT:PP/ABC/1-->PP/ABC"]},
        ],
    )
    # No work lookup is mocked, so the remover must not look for other works sharing these nodes
    mock_work_identifiers_edge_removal([])

    run_work_identifiers_edge_remover()

    check_work_identifiers_deleted_edges(set())


def test_work_identifiers_shared_node_keeps_edges_of_works_outside_window(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(remover_module, "WORK_LOOKUP_BATCH_SIZE", 1)
    add_path_work("inwindow", "axiell:NEW/axiell:S", "S")
    add_path_work("inwind02", "axiell:NEW2/axiell:S", "S")
    # Shares node axiell:S but was last merged outside the window
    add_path_work("outside1", "axiell:OTHER/axiell:S", "S", merged_time=OUTSIDE_WINDOW)
    # Still linked to axiell:S in the graph, but its current path points elsewhere
    add_path_work("outside2", "axiell:Z/axiell:T", "T", merged_time=OUTSIDE_WINDOW)

    mock_neptune_get_edges_response(
        ["inwind02", "inwindow"],
        edge_label="HAS_PATH_IDENTIFIER",
        results=[
            {"id": "inwindow", "edge_ids": ["HAS_PATH_IDENTIFIER:inwindow-->axiell:S"]},
            {"id": "inwind02", "edge_ids": ["HAS_PATH_IDENTIFIER:inwind02-->axiell:S"]},
        ],
    )
    mock_neptune_get_parent_edges_response(
        ["axiell:S"],
        results=[
            {
                "id": "axiell:S",
                "edge_ids": [
                    "HAS_PARENT:axiell:S-->axiell:NEW",
                    "HAS_PARENT:axiell:S-->axiell:NEW2",
                    "HAS_PARENT:axiell:S-->axiell:OTHER",
                    "HAS_PARENT:axiell:S-->axiell:Z",
                    "HAS_PARENT:axiell:S-->axiell:OLD",
                ],
            }
        ],
    )
    mock_neptune_get_path_identifier_works_response(
        ["axiell:S"],
        results=[
            {
                "id": "axiell:S",
                "source_ids": ["inwind02", "inwindow", "outside1", "outside2"],
            }
        ],
    )
    edges_to_remove = [
        "HAS_PARENT:axiell:S-->axiell:OLD",
        "HAS_PARENT:axiell:S-->axiell:Z",
    ]
    mock_work_identifiers_edge_removal(edges_to_remove)

    run_work_identifiers_edge_remover()

    check_work_identifiers_deleted_edges(set(edges_to_remove))
    # Works sharing the node are looked up by ID, one chunk at a time, without the window filter
    # Each lookup pages through search_after, so repeated queries are collapsed
    distinct_queries: list[dict] = []
    for query in MockElasticsearchClient.queries:
        if query not in distinct_queries:
            distinct_queries.append(query)
    assert distinct_queries[-2:] == [
        {
            "bool": {
                "must": [
                    {
                        "bool": {
                            "must": [
                                {"match": {"type": "Visible"}},
                                {"exists": {"field": "data.collectionPath.path"}},
                            ]
                        }
                    },
                    {"ids": {"values": [work_id]}},
                ]
            }
        }
        for work_id in ["outside1", "outside2"]
    ]


def test_work_identifiers_shared_node_across_batches(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # One work per batch, so each batch must keep the parent edge the other batch's work implies
    monkeypatch.setattr(remover_module, "BATCH_SIZE", 1)
    add_path_work("inwindow", "axiell:NEW/axiell:S", "S")
    add_path_work("inwind02", "axiell:NEW2/axiell:S", "S")

    for work_id in ["inwindow", "inwind02"]:
        mock_neptune_get_edges_response(
            [work_id],
            edge_label="HAS_PATH_IDENTIFIER",
            results=[
                {
                    "id": work_id,
                    "edge_ids": [f"HAS_PATH_IDENTIFIER:{work_id}-->axiell:S"],
                }
            ],
        )
    mock_neptune_get_parent_edges_response(
        ["axiell:S"],
        results=[
            {
                "id": "axiell:S",
                "edge_ids": [
                    "HAS_PARENT:axiell:S-->axiell:NEW",
                    "HAS_PARENT:axiell:S-->axiell:NEW2",
                    "HAS_PARENT:axiell:S-->axiell:OLD",
                ],
            }
        ],
    )
    mock_neptune_get_path_identifier_works_response(
        ["axiell:S"],
        results=[{"id": "axiell:S", "source_ids": ["inwind02", "inwindow"]}],
    )
    # The existing-ids mock matches its exact ID list, so a repeated yield would fail here
    edges_to_remove = ["HAS_PARENT:axiell:S-->axiell:OLD"]
    mock_work_identifiers_edge_removal(edges_to_remove)

    run_work_identifiers_edge_remover()

    check_work_identifiers_deleted_edges(set(edges_to_remove))


def test_work_identifiers_parent_edge_safety_threshold() -> None:
    add_path_work("moved001", "axiell:NEW/axiell:A", "A")

    mock_neptune_get_edges_response(
        ["moved001"],
        edge_label="HAS_PATH_IDENTIFIER",
        results=[
            {"id": "moved001", "edge_ids": ["HAS_PATH_IDENTIFIER:moved001-->axiell:A"]}
        ],
    )
    mock_neptune_get_parent_edges_response(
        ["axiell:A"],
        results=[{"id": "axiell:A", "edge_ids": ["HAS_PARENT:axiell:A-->axiell:OLD"]}],
    )
    mock_neptune_get_path_identifier_works_response(
        ["axiell:A"], results=[{"id": "axiell:A", "source_ids": ["moved001"]}]
    )
    edges_to_remove = ["HAS_PARENT:axiell:A-->axiell:OLD"]
    # One of four path identifier HAS_PARENT edges, although tiny against HAS_PATH_IDENTIFIER
    mock_work_identifiers_edge_removal(
        edges_to_remove, has_path_identifier_count=100_000, has_parent_count=4
    )

    with pytest.raises(
        ValueError, match="Fractional change 0.25 exceeds threshold 0.2!"
    ):
        run_work_identifiers_edge_remover()

    run_work_identifiers_edge_remover(force_pass=True)
    check_work_identifiers_deleted_edges(set(edges_to_remove))


def mock_neptune_get_disconnected_path_identifier_nodes(node_ids: list) -> None:
    add_neptune_mock_response(
        expected_query="MATCH (n: PathIdentifier) WHERE NOT (n)-[:HAS_PATH_IDENTIFIER]-() RETURN id(n) AS id",
        expected_params={},
        mock_results=[{"id": i} for i in node_ids],
    )


def mock_work_identifiers_node_removal(
    disconnected_ids: list[str], nodes_to_remove: list[str], node_count: int = 1000
) -> None:
    mock_neptune_get_total_node_count("PathIdentifier", node_count)
    mock_neptune_get_disconnected_path_identifier_nodes(disconnected_ids)
    mock_neptune_get_existing_nodes_response(nodes_to_remove)
    mock_neptune_delete_nodes_response(nodes_to_remove)
    mock_neptune_secrets("dev")
    mock_es_secrets(service_name="graph_extractor", pipeline_date="dev")


def run_work_identifiers_node_remover(force_pass: bool = False) -> None:
    MockElasticsearchClient.apply_range_filters = True
    lambda_handler(
        {
            "transformer_type": "catalogue_work_identifiers",
            "entity_type": "nodes",
            "pipeline_date": "dev",
            "graph_date": "dev",
            "window": WORK_IDENTIFIERS_WINDOW,
            "force_pass": force_pass,
        },
        None,
    )


def check_work_identifiers_deleted_nodes(expected_ids: set[str]) -> None:
    s3_uri = get_remover_s3_uri(
        "dev",
        "dev",
        "windows/20250101T1145-20250101T1200/deleted_ids/catalogue_work_identifiers__nodes.parquet",
    )
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        ids = pl.Series(df.select(pl.first())).to_list() if len(df.columns) else []
        assert set(ids) == expected_ids


def test_work_identifiers_nodes_keeps_disconnected_node_with_visible_work() -> None:
    # Merged after the window, so its Work node and HAS_PATH_IDENTIFIER edge are not in the graph yet
    add_path_work(
        "future01", "axiell:P/axiell:C", "C", merged_time="2025-01-01T12:10:00Z"
    )
    # RefNo mode: the full path is the path identifier
    add_path_work("refno001", "PP/ABC/1", "PP/ABC/1", merged_time=OUTSIDE_WINDOW)
    mock_work_identifiers_node_removal(
        disconnected_ids=["axiell:C", "PP/ABC/1", "axiell:GONE"],
        nodes_to_remove=["axiell:GONE"],
    )

    run_work_identifiers_node_remover()

    check_work_identifiers_deleted_nodes({"axiell:GONE"})


def test_work_identifiers_nodes_removes_node_left_behind_by_moved_record() -> None:
    # The work now maps to axiell:A2, so nothing maps to its old node axiell:A
    add_path_work("moved001", "axiell:NEW/axiell:A2", "A2", merged_time=OUTSIDE_WINDOW)
    mock_work_identifiers_node_removal(
        disconnected_ids=["axiell:A"], nodes_to_remove=["axiell:A"]
    )

    run_work_identifiers_node_remover()

    check_work_identifiers_deleted_nodes({"axiell:A"})
    # The scan covers the whole merged index, not just the window
    assert MockElasticsearchClient.queries[-1] == {
        "bool": {
            "must": [
                {"match": {"type": "Visible"}},
                {"exists": {"field": "data.collectionPath.path"}},
            ]
        }
    }


def test_work_identifiers_nodes_skips_es_scan_without_disconnected_nodes() -> None:
    mock_work_identifiers_node_removal(disconnected_ids=[], nodes_to_remove=[])

    run_work_identifiers_node_remover()

    check_work_identifiers_deleted_nodes(set())
    assert MockElasticsearchClient.queries == []


def test_work_identifiers_nodes_threshold_counts_only_removed_ids() -> None:
    # Five disconnected of ten nodes, but four still have a work, so one in ten is removed
    for i in range(4):
        add_path_work(f"future0{i}", f"axiell:P/axiell:C{i}", f"C{i}")
    disconnected = [f"axiell:C{i}" for i in range(4)] + ["axiell:GONE"]
    mock_work_identifiers_node_removal(
        disconnected_ids=disconnected, nodes_to_remove=["axiell:GONE"], node_count=10
    )

    run_work_identifiers_node_remover()

    check_work_identifiers_deleted_nodes({"axiell:GONE"})


def test_work_identifiers_nodes_threshold_still_fails_on_large_removal() -> None:
    mock_work_identifiers_node_removal(
        disconnected_ids=["axiell:GONE1", "axiell:GONE2", "axiell:GONE3"],
        nodes_to_remove=["axiell:GONE1", "axiell:GONE2", "axiell:GONE3"],
        node_count=10,
    )

    with pytest.raises(ValueError, match="Fractional change 0.3 exceeds threshold"):
        run_work_identifiers_node_remover()
