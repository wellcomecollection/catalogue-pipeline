from ingestor.models.neptune.query_result import WorkHierarchy
from tests.test_utils import get_work_hierarchy_item


def test_is_collection_root_false_when_no_ancestors_and_no_children() -> None:
    hierarchy = WorkHierarchy(id="some_id")
    assert hierarchy.is_collection_root is False


def test_is_collection_root_true_when_no_ancestors_and_has_children() -> None:
    hierarchy = WorkHierarchy(
        id="some_id", children=[get_work_hierarchy_item("child", "Child")]
    )
    assert hierarchy.is_collection_root is True


def test_is_collection_root_false_when_ancestors_present() -> None:
    # A work in the middle of a hierarchy has both ancestors and children
    hierarchy = WorkHierarchy(
        id="some_id",
        ancestors=[get_work_hierarchy_item("parent", "Parent")],
        children=[get_work_hierarchy_item("child", "Child")],
    )
    assert hierarchy.is_collection_root is False
