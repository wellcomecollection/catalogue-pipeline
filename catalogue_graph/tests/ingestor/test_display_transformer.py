from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.display.concept import DisplayConcept, DisplaySubject
from ingestor.models.display.id_label import DisplayIdLabel
from ingestor.models.display.identifier import DisplayIdentifier, DisplayIdentifierType
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.node import WorkNode
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
    WorkHierarchyItem,
)
from ingestor.transformers.work_display_transformer import DisplayWorkTransformer
from models.graph_node import Work
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Subject
from tests.test_utils import (
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


def test_short_description_truncates_at_first_sentence() -> None:
    extracted = get_work_fixture()
    extracted.work.data.description = "First sentence. Second sentence."
    assert DisplayWorkTransformer(extracted).short_description == "First sentence."


def test_short_description_without_sentence_terminator() -> None:
    extracted = get_work_fixture()
    extracted.work.data.description = "No terminator here"
    assert DisplayWorkTransformer(extracted).short_description == "No terminator here"


def test_short_description_none_when_no_description() -> None:
    extracted = get_work_fixture()
    extracted.work.data.description = None
    assert DisplayWorkTransformer(extracted).short_description is None


def test_is_archive_root_false_by_default() -> None:
    extracted = get_work_fixture()
    assert DisplayWorkTransformer(extracted).is_archive_root is False


def test_is_archive_root_true_when_no_ancestors_and_has_children() -> None:
    extracted = get_work_fixture()
    extracted.hierarchy.children = [
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
    ]
    assert DisplayWorkTransformer(extracted).is_archive_root is True


def test_archive_type_from_collection_path_label() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = CollectionPath(
        path="PPRAS/A/2/1", label="PP/RAS/A.2/1"
    )
    assert DisplayWorkTransformer(extracted).archive_type == DisplayIdLabel(
        id="PP", label="Personal Papers", type="ArchiveType"
    )


def test_archive_type_none_for_unknown_prefix() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = CollectionPath(path="XYZ/1", label="XYZ/1")
    assert DisplayWorkTransformer(extracted).archive_type is None


def test_archive_type_none_when_no_collection_path() -> None:
    extracted = get_work_fixture()
    extracted.work.data.collection_path = None
    assert DisplayWorkTransformer(extracted).archive_type is None


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
