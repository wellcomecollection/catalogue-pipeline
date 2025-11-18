from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import WorkHierarchy, WorkHierarchyItem
from ingestor.transformers.work_query_transformer import QueryWorkTransformer
from models.graph_node import Work
from tests.test_utils import load_json_fixture


def get_ancestor_node(collection_path: str | None) -> WorkNode:
    return WorkNode.model_validate(
        {
            "~id": "123",
            "~labels": ["Work"],
            "~properties": Work(
                id="123", label="123", type="Work", collection_path=collection_path
            ),
        }
    )


def get_work_with_ancestor(
    collection_path: str | None, ancestor_collection_path: str | None
) -> VisibleExtractedWork:
    fixture = load_json_fixture("ingestor/single_merged.json")
    fixture |= {"data": {"collectionPath": {"path": collection_path}}}
    work = VisibleMergedWork.model_validate(fixture)

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[
                WorkHierarchyItem(
                    work=get_ancestor_node(collection_path=ancestor_collection_path),
                    parts=1,
                )
            ],
        ),
        concepts=[],
    )


def test_collection_path_expansion() -> None:
    extracted = get_work_with_ancestor(
        collection_path="456/789", ancestor_collection_path="123/456"
    )
    assert QueryWorkTransformer(extracted).collection_path == "123/456/789"

    extracted = get_work_with_ancestor(
        collection_path="456/789", ancestor_collection_path="789"
    )
    assert QueryWorkTransformer(extracted).collection_path == "456/789"


def test_collection_path_no_expansion() -> None:
    extracted = get_work_with_ancestor(
        collection_path="123/456/789", ancestor_collection_path="456/789"
    )
    assert QueryWorkTransformer(extracted).collection_path == "123/456/789"
