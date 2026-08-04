from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
)
from models.pipeline.collection_path import CollectionPath
from tests.test_utils import (
    get_work_hierarchy_item,
    get_work_with_ancestor,
    load_json_fixture,
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


def test_collection_root_with_ancestors() -> None:
    extracted = get_work_with_ancestor(
        ancestor_id="root_id", ancestor_label="Root title"
    )
    assert AggregateWorkTransformer(extracted).collection_root == AggregatableField(
        id="root_id", label="Root title"
    )


def test_collection_root_when_work_is_root() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)
    work.state.canonical_id = "this_work_id"
    work.data.title = "This work title"

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(
            id="some_id",
            ancestors=[],
            children=[get_work_hierarchy_item("child", "Child")],
        ),
        concepts=[],
    )

    assert AggregateWorkTransformer(extracted).collection_root == AggregatableField(
        id="this_work_id", label="This work title"
    )


def test_collection_root_none_when_no_hierarchy() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(id="some_id", ancestors=[], children=[]),
        concepts=[],
    )

    assert AggregateWorkTransformer(extracted).collection_root is None


def test_collection_root_none_when_root_ancestor_has_no_label() -> None:
    # AggregatableField.label is required, so we cannot construct one for a root
    # ancestor with no label -- the field is omitted entirely in this case.
    extracted = get_work_with_ancestor(ancestor_label=None)
    assert AggregateWorkTransformer(extracted).collection_root is None
