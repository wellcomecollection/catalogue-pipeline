import json
import os
from typing import Any

from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import WorkHierarchy, WorkHierarchyItem
from models.events import BulkLoaderEvent
from models.graph_edge import BaseEdge
from models.graph_node import Work
from models.pipeline.access_condition import AccessCondition
from models.pipeline.identifier import Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation, LocationType
from tests.mocks import MockElasticsearchClient, MockSmartOpen
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


def get_work_hierarchy_item(
    work_id: str,
    label: str | None = None,
    parts: int = 1,
    availabilities: list[str] | None = None,
) -> WorkHierarchyItem:
    """Build a hierarchy item (an ancestor or a child) for a work with the given id."""
    return WorkHierarchyItem(
        work=WorkNode.model_validate(
            {
                "~id": work_id,
                "~labels": ["Work"],
                "~properties": Work(
                    id=work_id,
                    label=label,
                    type="Work",
                    availabilities=availabilities or [],
                ),
            }
        ),
        parts=parts,
    )


def get_item_with_access_conditions(*access_conditions: AccessCondition) -> Item:
    """Build an item with a single digital location carrying the given access conditions."""
    return Item(
        id=Unidentifiable(),
        locations=[
            DigitalLocation(
                url="https://example.com/1",
                location_type=LocationType(id="iiif-presentation"),
                access_conditions=list(access_conditions),
            )
        ],
    )


def get_work_with_ancestor(
    ancestor_id: str = "123", ancestor_label: str | None = "123"
) -> VisibleExtractedWork:
    """Build an extracted work with a single ancestor, and no children."""
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[get_work_hierarchy_item(ancestor_id, ancestor_label)],
        ),
        concepts=[],
    )


def add_mock_transformer_outputs(
    transformers: list[TransformerType],
    pipeline_date: str,
    graph_date: str,
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """

    for transformer in transformers:
        event = BulkLoaderEvent(
            graph_date=graph_date,
            pipeline_date=pipeline_date,
            transformer_type=transformer,
            entity_type="nodes",
        )
        s3_uri = event.get_s3_uri()

        try:
            fixture = load_fixture(f"bulk_load/{transformer}__nodes.csv").decode()
            MockSmartOpen.mock_s3_file(s3_uri, fixture)
        except FileNotFoundError:
            # We do not have mocks for all possible files
            pass


def add_mock_transformer_outputs_for_ontologies(
    ontologies: list[OntologyType],
    pipeline_date: str = "dev",
    graph_date: str = "dev",
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    transformers = []
    for ontology in ontologies:
        transformers += get_transformers_from_ontology(ontology)

    return add_mock_transformer_outputs(transformers, pipeline_date, graph_date)


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
