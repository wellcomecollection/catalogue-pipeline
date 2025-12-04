from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import ExtractedConcept, WorkHierarchy
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
)
from models.pipeline.concept import Subject
from models.pipeline.id_label import Language
from tests.test_utils import (
    load_json_fixture,
)


def get_work_fixture() -> VisibleExtractedWork:
    fixture = load_json_fixture("ingestor/single_merged.json")
    work = VisibleMergedWork.model_validate(fixture)

    return VisibleExtractedWork(
        work=work, hierarchy=WorkHierarchy(id="some_id"), concepts=[]
    )


def test_marc_languages() -> None:
    extracted = get_work_fixture()

    # Replace label with MARC label
    extracted.work.data.languages = [Language(id="egy", label="Ancient Egyptian")]
    assert list(AggregateWorkTransformer(extracted).languages)[0] == AggregatableField(
        id="egy", label="Egyptian"
    )

    extracted.work.data.languages = [Language(id="dut", label="Some label")]
    assert list(AggregateWorkTransformer(extracted).languages)[0] == AggregatableField(
        id="dut", label="Dutch"
    )

    # If the language does not exist in the mapping file, preserve the original label
    extracted.work.data.languages = [Language(id="some_code", label="Some label")]
    assert list(AggregateWorkTransformer(extracted).languages)[0] == AggregatableField(
        id="some_code", label="Some label"
    )


def test_concept_standard_labels() -> None:
    extracted = get_work_fixture()

    malaria_concept_fixture = load_json_fixture("neptune/extracted_concept.json")
    extracted.concepts = [ExtractedConcept.model_validate(malaria_concept_fixture)]
    subject = Subject.model_validate(load_json_fixture("ingestor/single_subject.json"))
    extracted.work.data.subjects = [subject]

    # Use standard label
    assert list(AggregateWorkTransformer(extracted).subjects)[0] == AggregatableField(
        id="w5ewpsaw", label="Malaria"
    )


def test_concept_aggregation_deduplication() -> None:
    extracted = get_work_fixture()

    malaria_concept_fixture = load_json_fixture("neptune/extracted_concept.json")
    extracted.concepts = [ExtractedConcept.model_validate(malaria_concept_fixture)]
    subject = Subject.model_validate(load_json_fixture("ingestor/single_subject.json"))
    extracted.work.data.subjects = [subject, subject]

    # Deduplicate concepts with the same label
    assert len(list(AggregateWorkTransformer(extracted).subjects)) == 1
