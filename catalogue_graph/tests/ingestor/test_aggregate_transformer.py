from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
)
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import OnlineRequest, ViewOnline
from models.pipeline.access_status import Open, Restricted
from models.pipeline.collection_path import CollectionPath
from tests.test_utils import (
    get_item_with_access_conditions,
    get_work_hierarchy_item,
    get_work_with_ancestor,
    load_json_fixture,
)


def test_archive_category_from_collection_path_label() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert AggregateWorkTransformer(extracted).archive_category == AggregatableField(
        id="PP", label="Personal papers"
    )


def test_archive_category_none_when_no_collection_path() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = None
    assert AggregateWorkTransformer(extracted).archive_category is None


def test_access_methods() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.items = [
        get_item_with_access_conditions(
            AccessCondition(method=ViewOnline, status=Open),
            AccessCondition(method=OnlineRequest, status=Restricted),
        ),
        # Methods shared with another item are only aggregated once
        get_item_with_access_conditions(AccessCondition(method=ViewOnline)),
    ]

    assert list(AggregateWorkTransformer(extracted).access_methods) == [
        AggregatableField(id="view-online", label="View online"),
        AggregatableField(id="online-request", label="Online request"),
    ]


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
    work.data.collection_path = CollectionPath(path="PPRAS", label="PP/RAS")

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
