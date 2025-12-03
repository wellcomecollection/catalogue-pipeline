from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import WorkHierarchy, WorkHierarchyItem
from ingestor.transformers.work_query_transformer import QueryWorkTransformer
from models.graph_node import Work
from models.pipeline.collection_path import CollectionPath
from models.pipeline.work_state import WorkAncestor, WorkRelations
from tests.test_utils import load_json_fixture


def get_work_with_ancestor() -> VisibleExtractedWork:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    ancestor_node = WorkNode.model_validate(
        {
            "~id": "123",
            "~labels": ["Work"],
            "~properties": Work(id="123", label="123", type="Work"),
        }
    )

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[
                WorkHierarchyItem(
                    work=ancestor_node,
                    parts=1,
                )
            ],
        ),
        concepts=[],
    )


def test_collection_path_expansion() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "123/456"
    assert QueryWorkTransformer(extracted).collection_path == "123/456/789"

    extracted.work.data.collection_path = CollectionPath(path="456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "789"
    assert QueryWorkTransformer(extracted).collection_path == "456/789"


def test_collection_path_no_expansion() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="123/456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "456/789"
    assert QueryWorkTransformer(extracted).collection_path == "123/456/789"


def test_series_ancestor_deduplication() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.state.relations = WorkRelations(
        ancestors=[
            WorkAncestor(
                title="Some ancestor title",
                work_type="Series",
                depth=0,
                num_children=0,
                num_descendents=0,
            )
        ]
    )

    extracted.hierarchy.ancestors[0].work.properties.label = "Some ancestor title."
    assert list(QueryWorkTransformer(extracted).part_of_titles) == [
        "Some ancestor title."
    ]


def test_series_ancestor_no_deduplication() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.state.relations = WorkRelations(
        ancestors=[
            WorkAncestor(
                title="Some series title",
                work_type="Series",
                depth=0,
                num_children=0,
                num_descendents=0,
            )
        ]
    )

    extracted.hierarchy.ancestors[0].work.properties.label = "Some ancestor title."
    assert list(QueryWorkTransformer(extracted).part_of_titles) == [
        "Some series title",
        "Some ancestor title.",
    ]


def test_identifiers_includes_work_canonical_id() -> None:
    extracted = get_work_with_ancestor()
    # Set up canonical_id and other identifiers
    extracted.work.state.canonical_id = "canonical_id_1"
    extracted.work.state.source_identifier.value = "b_number"
    identifiers = list(QueryWorkTransformer(extracted).identifiers)
    assert "canonical_id_1" in identifiers
    assert "b_number" in identifiers
