from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import WorkHierarchy, WorkHierarchyItem
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
)
from models.graph_node import Work
from models.pipeline.collection_path import CollectionPath
from tests.test_utils import load_json_fixture


def get_work_with_ancestor() -> VisibleExtractedWork:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    ancestor_node = WorkNode.model_validate(
        {
            "~id": "123",
            "~labels": ["Work"],
            "~properties": Work(id="root_id", label="Root title", type="Work"),
        }
    )

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[WorkHierarchyItem(work=ancestor_node, parts=1)],
        ),
        concepts=[],
    )


def test_archive_type_from_collection_path_label() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert AggregateWorkTransformer(extracted).archive_type == AggregatableField(
        id="PP", label="Personal Papers"
    )


def test_archive_type_none_when_no_collection_path() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = None
    assert AggregateWorkTransformer(extracted).archive_type is None


def test_archive_root_with_ancestors() -> None:
    extracted = get_work_with_ancestor()
    assert AggregateWorkTransformer(extracted).archive_root == AggregatableField(
        id="root_id", label="Root title"
    )


def test_archive_root_when_work_is_root() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)
    work.state.canonical_id = "this_work_id"
    work.data.title = "This work title"

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[],
            children=[
                WorkHierarchyItem(
                    work=WorkNode.model_validate(
                        {
                            "~id": "child",
                            "~labels": ["Work"],
                            "~properties": Work(id="child", label="Child", type="Work"),
                        }
                    ),
                    parts=1,
                )
            ],
        ),
        concepts=[],
    )

    assert AggregateWorkTransformer(extracted).archive_root == AggregatableField(
        id="this_work_id", label="This work title"
    )


def test_archive_root_none_when_no_hierarchy() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(id="some_id", ancestors=[], children=[]),
        concepts=[],
    )

    assert AggregateWorkTransformer(extracted).archive_root is None
