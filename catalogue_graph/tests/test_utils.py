import json
import os
from typing import Any

from test_mocks import MockElasticsearchClient, MockSmartOpen

from models.graph_edge import BaseEdge
from utils.ontology import get_transformers_from_ontology
from utils.types import OntologyType, TransformerType, WorkStatus


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
    transformers: list[TransformerType], pipeline_date: str = "dev"
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    for transformer in transformers:
        bucket_name = "wellcomecollection-catalogue-graph"
        s3_uri = f"s3://{bucket_name}/graph_bulk_loader/{pipeline_date}/{transformer}__nodes.csv"

        try:
            fixture = load_fixture(f"bulk_load/{transformer}__nodes.csv").decode()
            MockSmartOpen.mock_s3_file(s3_uri, fixture)
        except FileNotFoundError:
            # We do not have mocks for all possible files
            pass


def add_mock_transformer_outputs_for_ontologies(
    ontologies: list[OntologyType], pipeline_date: str = "dev"
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    transformers = []
    for ontology in ontologies:
        transformers += get_transformers_from_ontology(ontology)

    return add_mock_transformer_outputs(transformers, pipeline_date)


def add_mock_merged_documents(
    pipeline_date: str = "dev",
    work_status: WorkStatus | None = None,
) -> None:
    index_name = f"works-denormalised-{pipeline_date}"

    if work_status is None:
        fixture = load_jsonl_fixture("merged_works/sample.jsonl")
    else:
        fixture = load_jsonl_fixture(f"merged_works/{work_status.lower()}.jsonl")

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
