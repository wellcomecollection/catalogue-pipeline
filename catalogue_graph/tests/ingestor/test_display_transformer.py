from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.display.concept import DisplayConcept, DisplaySubject
from ingestor.models.display.identifier import DisplayIdentifier, DisplayIdentifierType
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
)
from ingestor.transformers.work_display_transformer import DisplayWorkTransformer
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


def test_concept_standard_labels() -> None:
    extracted = get_work_fixture()

    assert list(DisplayWorkTransformer(extracted).subjects) == [
        DisplaySubject(
            id="w5ewpsaw",
            label="Malaria - prevention & control",
            standard_label="Malaria",
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
                    standard_label="Malaria",
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
