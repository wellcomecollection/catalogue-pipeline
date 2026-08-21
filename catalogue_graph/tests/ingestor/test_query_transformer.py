from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
)
from ingestor.transformers.work_query_transformer import QueryWorkTransformer
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import OnlineRequest, ViewOnline
from models.pipeline.access_status import Open, Restricted
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Subject
from models.pipeline.work_state import WorkAncestor, WorkRelations
from tests.test_utils import (
    get_item_with_access_conditions,
    get_work_hierarchy_item,
    get_work_with_ancestor,
    load_json_fixture,
)


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


def test_concept_standard_labels() -> None:
    extracted = get_work_with_ancestor()

    malaria_concept_fixture = load_json_fixture("neptune/extracted_concept.json")
    extracted.concepts = [ExtractedConcept.model_validate(malaria_concept_fixture)]
    subject = Subject.model_validate(load_json_fixture("ingestor/single_subject.json"))
    extracted.work.data.subjects = [subject]

    # Use standard label
    assert list(QueryWorkTransformer(extracted).subject_labels) == ["Malaria"]


def test_identifiers_includes_work_canonical_id() -> None:
    extracted = get_work_with_ancestor()
    # Set up canonical_id and other identifiers
    extracted.work.state.canonical_id = "canonical_id_1"
    extracted.work.state.source_identifier.value = "b_number"
    identifiers = list(QueryWorkTransformer(extracted).identifiers)
    assert "canonical_id_1" in identifiers
    assert "b_number" in identifiers


def test_access_condition_ids() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.items = [
        get_item_with_access_conditions(
            AccessCondition(method=ViewOnline, status=Open),
            AccessCondition(method=OnlineRequest, status=Restricted),
        ),
        get_item_with_access_conditions(AccessCondition(method=ViewOnline)),
    ]

    transformer = QueryWorkTransformer(extracted)
    assert list(transformer.access_condition_method_ids) == [
        "view-online",
        "online-request",
        "view-online",
    ]
    # Access conditions without a status do not contribute a filterable status
    assert list(transformer.access_condition_status_ids) == ["open", "restricted"]


def test_collection_root_with_ancestors() -> None:
    extracted = get_work_with_ancestor(
        ancestor_id="root_id", ancestor_label="Root title"
    )

    transformer = QueryWorkTransformer(extracted)
    assert transformer.collection_root_id == "root_id"
    assert transformer.collection_root_title == "Root title"


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

    transformer = QueryWorkTransformer(extracted)
    assert transformer.collection_root_id == "this_work_id"
    assert transformer.collection_root_title == "This work title"


def test_collection_root_when_work_is_root_without_children() -> None:
    # Some collection roots have no children in the public catalogue, but are still roots
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)
    work.state.canonical_id = "this_work_id"
    work.data.title = "This work title"
    work.data.collection_path = CollectionPath(path="PPRAS", label="PP/RAS")

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(id="some_id", ancestors=[], children=[]),
        concepts=[],
    )

    transformer = QueryWorkTransformer(extracted)
    assert transformer.collection_root_id == "this_work_id"
    assert transformer.collection_root_title == "This work title"


def test_collection_root_when_no_hierarchy() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    extracted = VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(id="some_id", ancestors=[], children=[]),
        concepts=[],
    )

    transformer = QueryWorkTransformer(extracted)
    assert transformer.collection_root_id is None
    assert transformer.collection_root_title is None


def test_archive_category_from_collection_path_label() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert QueryWorkTransformer(extracted).archive_category_id == "PP"


def test_archive_category_none_for_unknown_prefix() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="XYZ/1", label="XYZ/1")
    assert QueryWorkTransformer(extracted).archive_category_id is None


def test_archive_category_none_when_no_collection_path() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = None
    assert QueryWorkTransformer(extracted).archive_category_id is None


def test_collection_path_sort_pads_numbers() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = CollectionPath(path="GC176/C/1")
    assert (
        QueryWorkTransformer(extracted).collection_path_sort
        == "GC0000000176/C/0000000001"
    )


def test_collection_path_sort_none_when_no_collection_path() -> None:
    extracted = get_work_with_ancestor()
    extracted.work.data.collection_path = None
    assert QueryWorkTransformer(extracted).collection_path_sort is None


def test_collection_path_sort_orders_archive_tree() -> None:
    """Sorting on `collection_path_sort` returns a fully open archive tree in order."""
    extracted = get_work_with_ancestor()

    def sort_key(path: str) -> str:
        extracted.work.data.collection_path = CollectionPath(path=path)
        sort_value = QueryWorkTransformer(extracted).collection_path_sort
        assert sort_value is not None
        return sort_value

    paths = [
        "PPEBC/B",
        "PPEBC/A/1/10",
        "PPEBC/A/2",
        "PPEBC/A/1/9",
        "PPEBC/A/1",
        "PPEBC",
    ]

    # Every work comes before its own children and after its preceding siblings,
    # with numbered siblings ordered by number rather than alphabetically (9 before 10).
    assert sorted(paths, key=sort_key) == [
        "PPEBC",
        "PPEBC/A/1",
        "PPEBC/A/1/9",
        "PPEBC/A/1/10",
        "PPEBC/A/2",
        "PPEBC/B",
    ]


def test_collection_path_sort_ignores_leading_zeroes_and_letters() -> None:
    """Numbers sort numerically despite leading zeroes or letters in the same segment.

    These are the paths used by the `works.collection-path-sort` test documents.
    """
    extracted = get_work_with_ancestor()

    def sort_key(path: str) -> str:
        extracted.work.data.collection_path = CollectionPath(path=path)
        sort_value = QueryWorkTransformer(extracted).collection_path_sort
        assert sort_value is not None
        return sort_value

    paths = ["SASRT/C10/1", "SASRT/C2/010", "SASRT/C2/010/1", "SASRT/C2/9"]

    assert sorted(paths, key=sort_key) == [
        "SASRT/C2/9",
        "SASRT/C2/010",
        "SASRT/C2/010/1",
        "SASRT/C10/1",
    ]
