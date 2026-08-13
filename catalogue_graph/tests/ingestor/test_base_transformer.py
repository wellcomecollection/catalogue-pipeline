from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.transformers.work_base_transformer import WorkBaseTransformer
from models.pipeline.collection_path import CollectionPath
from tests.test_utils import (
    get_work_hierarchy_item,
    get_work_with_ancestor,
    load_json_fixture,
)


def get_work_with_path(
    path: str | None, children: list[str] | None = None
) -> VisibleExtractedWork:
    """Build an extracted work with a given collection path, and no ancestors."""
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)
    work.data.collection_path = (
        None if path is None else CollectionPath(path=path, label=path)
    )

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            children=[get_work_hierarchy_item(c) for c in children or []],
        ),
        concepts=[],
    )


def test_collection_path_expansion() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "123/456"
    assert WorkBaseTransformer(extracted).collection_path_path == "123/456/789"

    extracted.work.data.collection_path = CollectionPath(path="456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "789"
    assert WorkBaseTransformer(extracted).collection_path_path == "456/789"


def test_collection_path_no_expansion() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="123/456/789")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "456/789"
    assert WorkBaseTransformer(extracted).collection_path_path == "123/456/789"


def test_collection_path_trailing_slash_removed() -> None:
    extracted = get_work_with_path("PPRAS/")
    assert WorkBaseTransformer(extracted).collection_path_path == "PPRAS"


def test_collection_path_expansion_with_trailing_slashes() -> None:
    # A small number of works have a trailing slash in their collection path, which must not
    # prevent the path from being expanded using ancestor paths
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="456/789/")
    extracted.hierarchy.ancestors[0].work.properties.collection_path = "123/456/"
    assert WorkBaseTransformer(extracted).collection_path_path == "123/456/789"


def test_collection_path_none_when_no_collection_path() -> None:
    extracted = get_work_with_path(None)
    assert WorkBaseTransformer(extracted).collection_path_path is None


def test_is_collection_root_true_when_path_has_no_slash() -> None:
    extracted = get_work_with_path("PPRAS", children=["child"])
    assert WorkBaseTransformer(extracted).is_collection_root is True


def test_is_collection_root_true_when_root_has_no_children() -> None:
    # Some collection roots have no children in the public catalogue, but are still roots
    extracted = get_work_with_path("PPRAS", children=[])
    assert WorkBaseTransformer(extracted).is_collection_root is True


def test_is_collection_root_true_when_path_has_a_trailing_slash() -> None:
    extracted = get_work_with_path("PPRAS/", children=["child"])
    assert WorkBaseTransformer(extracted).is_collection_root is True


def test_is_collection_root_false_when_path_has_a_slash() -> None:
    extracted = get_work_with_path("PPRAS/A/2/1", children=["child"])
    assert WorkBaseTransformer(extracted).is_collection_root is False


def test_is_collection_root_false_when_no_collection_path() -> None:
    extracted = get_work_with_path(None, children=["child"])
    assert WorkBaseTransformer(extracted).is_collection_root is False


def test_is_collection_root_false_when_collection_path_empty() -> None:
    extracted = get_work_with_path("")
    assert WorkBaseTransformer(extracted).is_collection_root is False


def test_is_collection_root_false_when_detached_from_ancestors() -> None:
    # A work whose ancestors are missing from the graph is not the root of its collection,
    # even though it sits at the top of the (incomplete) hierarchy we know about
    extracted = get_work_with_path("PPRAS/A/2/1", children=["child"])
    assert extracted.hierarchy.ancestors == []
    assert WorkBaseTransformer(extracted).is_collection_root is False
