import json
import os
from itertools import product
from typing import Any, Literal

from models.graph_edge import BaseEdge
from test_mocks import MockElasticsearchClient, MockSmartOpen
from utils.aws import VALID_SOURCE_FILES


def _get_fixture_path(file_name: str) -> str:
    return f"{os.path.dirname(__file__)}/fixtures/{file_name}"


def load_fixture(file_name: str) -> bytes:
    with open(_get_fixture_path(file_name), "rb") as f:
        return f.read()


def load_json_fixture(file_name: str) -> Any:
    with open(_get_fixture_path(file_name), "rb") as f:
        return json.loads(f.read().decode())


def load_jsonl_fixture(file_name: str) -> list[Any]:
    with open(_get_fixture_path(file_name)) as f:
        return [json.loads(line) for line in f]


def add_mock_transformer_outputs(
    sources: list[
        Literal["loc", "mesh", "wikidata_linked_loc", "wikidata_linked_mesh"]
    ],
    node_types: list[Literal["concepts", "locations", "names"]],
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    for source, node_type in product(sources, node_types):
        if (node_type, source) in VALID_SOURCE_FILES:
            MockSmartOpen.mock_s3_file(
                f"s3://wellcomecollection-neptune-graph-loader/{source}_{node_type}__nodes.csv",
                load_fixture(
                    f"{source}/transformer_output_{node_type}_nodes.csv"
                ).decode(),
            )


def add_mock_denormalised_documents() -> None:
    index_name = "works-denormalised"
    fixture = load_jsonl_fixture("catalogue/denormalised_works_example.jsonl")
    for json_item in fixture:
        MockElasticsearchClient.index(
            index_name, json_item["state"]["canonicalId"], json_item
        )


def check_bulk_load_edge(all_edges: list[BaseEdge], expected_edge: BaseEdge) -> None:
    filtered_edges = [
        edge
        for edge in all_edges
        if edge.from_id == expected_edge.from_id and edge.to_id == expected_edge.to_id
    ]

    error_message = (
        f"Check for edge {expected_edge.from_id}-->{expected_edge.to_id} failed."
    )
    assert len(filtered_edges) == 1, error_message
    assert filtered_edges[0] == expected_edge, error_message
