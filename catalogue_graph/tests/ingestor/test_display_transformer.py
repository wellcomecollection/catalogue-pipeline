from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.display.archive import DisplayArchive
from ingestor.models.display.collection import DisplayCollection
from ingestor.models.display.concept import DisplayConcept, DisplaySubject
from ingestor.models.display.id_label import DisplayIdLabel
from ingestor.models.display.identifier import DisplayIdentifier, DisplayIdentifierType
from ingestor.models.display.relation import DisplayRelation
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
)
from ingestor.transformers.work_display_transformer import DisplayWorkTransformer
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Subject
from tests.test_utils import (
    get_work_hierarchy_item,
    get_work_with_ancestor,
    load_json_fixture,
)


def get_work_fixture() -> VisibleExtractedWork:
    fixture = load_json_fixture("ingestor/single_merged.json")
    malaria_concept_fixture = load_json_fixture("neptune/extracted_concept.json")
    work = VisibleMergedWork.model_validate(fixture)

    work.data.subjects = [
        Subject.model_validate(load_json_fixture("ingestor/single_subject.json"))
    ]

    return VisibleExtractedWork(
        work=work,
        hierarchy=WorkHierarchy(id="some_id"),
        concepts=[ExtractedConcept.model_validate(malaria_concept_fixture)],
    )


def test_archive_category_from_collection_path_label() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert DisplayWorkTransformer(extracted).archive == DisplayArchive(
        category=DisplayIdLabel(
            id="PP", label="Personal papers", type="ArchiveCategory"
        )
    )


def test_archive_none_for_unknown_prefix() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = CollectionPath(path="XYZ/1", label="XYZ/1")
    assert DisplayWorkTransformer(extracted).archive is None


def test_archive_none_when_no_collection_path() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = None
    assert DisplayWorkTransformer(extracted).archive is None


def test_collection_none_when_no_hierarchy() -> None:
    # The work has an archive category, but is not part of a collection hierarchy
    extracted = get_work_fixture()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert DisplayWorkTransformer(extracted).collection is None


def test_collection_with_ancestors() -> None:
    extracted = get_work_with_ancestor(
        ancestor_id="root_id", ancestor_label="Root title"
    )
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )

    assert DisplayWorkTransformer(extracted).collection == DisplayCollection(
        root=DisplayRelation(
            id="root_id", title="Root title", totalParts=1, type="Work"
        ),
        is_root=None,
    )


def test_collection_when_work_is_root() -> None:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)
    work.state.canonical_id = "this_work_id"
    work.data.title = "This work title"
    work.data.work_type = "Collection"
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

    assert DisplayWorkTransformer(extracted).collection == DisplayCollection(
        root=DisplayRelation(
            id="this_work_id",
            title="This work title",
            referenceNumber="PP/RAS",
            totalParts=1,
            type="Collection",
        ),
        is_root=True,
    )


def test_concept_standard_labels() -> None:
    extracted = get_work_fixture()

    assert list(DisplayWorkTransformer(extracted).subjects) == [
        DisplaySubject(
            id="w5ewpsaw",
            label="Malaria",
            identifiers=[
                DisplayIdentifier(
                    value="D008288Q000517",
                    type="Identifier",
                    identifierType=DisplayIdentifierType(
                        id="nlm-mesh",
                        type="IdentifierType",
                        label="Medical Subject Headings (MeSH) identifier",
                    ),
                )
            ],
            concepts=[
                DisplayConcept(
                    id="buy5ngy9",
                    label="Malaria",
                    identifiers=[
                        DisplayIdentifier(
                            value="malaria",
                            type="Identifier",
                            identifierType=DisplayIdentifierType(
                                id="label-derived",
                                type="IdentifierType",
                                label="Identifier derived from the label of the referent",
                            ),
                        )
                    ],
                )
            ],
        )
    ]
