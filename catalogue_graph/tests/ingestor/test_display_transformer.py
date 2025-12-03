from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
)
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
