import json
from datetime import datetime
from typing import Any, cast

import polars as pl
import pytest
from freezegun import freeze_time

import config
from ingestor.extractors.base_extractor import ConceptRelatedQuery
from ingestor.models.debug.work import (
    DeletedWorkDebug,
    InvisibleWorkDebug,
    RedirectedWorkDebug,
    WorkDebugSource,
)
from ingestor.models.display.identifier import DisplayIdentifier, DisplayIdentifierType
from ingestor.models.indexable_concept import (
    ConceptDescription,
    ConceptDisplay,
    ConceptIdentifier,
    ConceptQuery,
    ConceptRelatedTo,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.models.indexable_work import (
    DeletedIndexableWork,
    InvisibleIndexableWork,
    RedirectedIndexableWork,
)
from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from ingestor.models.shared.merge_candidate import MergeCandidate
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
    IngestorLoaderLambdaEvent,
)
from ingestor.queries.concept_queries import (
    BROADER_THAN_QUERY,
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    FIELDS_OF_WORK_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    HAS_FOUNDER_QUERY,
    NARROWER_THAN_QUERY,
    PEOPLE_QUERY,
    RELATED_TO_QUERY,
    RELATED_TOPICS_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
)
from ingestor.steps.ingestor_loader import handler
from models.events import IncrementalWindow, PipelineIndexDates
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identified, SourceIdentifier
from tests.mocks import (
    MockCloudwatchClient,
    MockElasticsearchClient,
    MockRequest,
    MockSmartOpen,
    add_neptune_mock_response,
    mock_es_secrets,
)
from tests.test_utils import (
    add_mock_merged_documents,
    load_json_fixture,
)
from utils.timezone import convert_datetime_to_utc_iso
from utils.types import IngestorType

MOCK_CONCEPT_ID = "jbxfbpzq"
MOCK_JOB_ID = "20250929T12:00"
MOCK_PIPELINE_DATE = "2025-01-01"
MOCK_INDEX_DATE = "2025-03-01"


def make_loader_event(
    pass_objects_to_index: bool,
    ingestor_type: IngestorType = "concepts",
    job_id: str = MOCK_JOB_ID,
) -> IngestorLoaderLambdaEvent:
    return IngestorLoaderLambdaEvent(
        ingestor_type=ingestor_type,
        pipeline_date=MOCK_PIPELINE_DATE,
        index_dates=PipelineIndexDates.model_validate({ingestor_type: MOCK_INDEX_DATE}),
        job_id=job_id,
        pass_objects_to_index=pass_objects_to_index,
        window=IncrementalWindow.model_validate({"end_time": "2025-01-01T12:00"}),
    )


def make_expected_indexer_event(
    loader_event: IngestorLoaderLambdaEvent,
    s3_filename: str,
    record_count: int,
    include_objects: bool,
) -> IngestorIndexerLambdaEvent:
    base_kwargs = loader_event.model_dump(exclude={"pass_objects_to_index"})

    if not include_objects:
        return IngestorIndexerLambdaEvent(**base_kwargs)

    return IngestorIndexerLambdaEvent(
        **base_kwargs,
        objects_to_index=[
            IngestorIndexerObject(
                s3_uri=(
                    f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/"
                    f"{loader_event.get_path_prefix()}/{s3_filename}"
                ),
                content_length=1,
                record_count=record_count,
            )
        ],
    )


def mock_merged_work() -> None:
    """Include a single work containing a single concept in the merged index"""
    fixture = {
        "data": {
            "contributors": [
                {
                    "agent": {
                        "id": {
                            "canonicalId": MOCK_CONCEPT_ID,
                            "sourceIdentifier": {
                                "identifier_type": {"id": "nlm-mesh"},
                                "ontology_type": "Concept",
                                "value": "123",
                            },
                        },
                        "label": "Some label",
                        "type": "Person",
                    }
                }
            ],
        },
        "state": {"canonicalId": "some-work-id"},
    }

    index_name = f"works-denormalised-{MOCK_PIPELINE_DATE}"
    MockElasticsearchClient.index(index_name, "123", fixture)

    mock_es_secrets("graph_extractor", MOCK_PIPELINE_DATE)


def add_mock_related(ids: list[str], related_ids: list[str], query: str) -> None:
    relationship_types = {"tzrtx26u": "has_sibling"}

    response = []
    if len(related_ids) > 0:
        response.append(
            {
                "id": MOCK_CONCEPT_ID,
                "related": [
                    {
                        "id": i,
                        "count": 1,
                        "relationship_type": relationship_types.get(i),
                    }
                    for i in related_ids
                ],
            }
        )

    _add_neptune_mock_response(ids, query, response)


def _add_neptune_mock_response(
    ids: list[str], expected_query: str, mock_results: list[dict]
) -> None:
    expected_params = {
        "ignored_wikidata_ids": ["Q5", "Q151885"],
        "related_to_limit": 10,
        "shared_works_count_threshold": 3,
        "ids": ids,
    }
    add_neptune_mock_response(expected_query, expected_params, mock_results)


def add_mock_responses_for_ids(ids: list[str], fixture_prefix: str = "") -> None:
    response = load_json_fixture(f"neptune/{fixture_prefix}concept_query.json")
    _add_neptune_mock_response(ids, CONCEPT_QUERY, response)

    response = load_json_fixture(f"neptune/{fixture_prefix}concept_same_as_query.json")
    _add_neptune_mock_response(ids, SAME_AS_CONCEPT_QUERY, response)

    response = load_json_fixture(f"neptune/{fixture_prefix}concept_types_query.json")
    _add_neptune_mock_response(ids, CONCEPT_TYPE_QUERY, response)

    response = load_json_fixture(f"neptune/{fixture_prefix}source_concepts_query.json")
    _add_neptune_mock_response(ids, SOURCE_CONCEPT_QUERY, response)


def mock_neptune_responses(include: list[ConceptRelatedQuery]) -> None:
    add_mock_responses_for_ids([MOCK_CONCEPT_ID])

    broader_than_ids = []
    if "broader_than" in include:
        broader_than_ids = ["hstuwwsu", "hv6pemej", "ugcgqepy"]
        add_mock_responses_for_ids(broader_than_ids, fixture_prefix="broader_than/")

    related_to_ids = []
    if "related_to" in include:
        related_to_ids = ["tzrtx26u"]
        add_mock_responses_for_ids(related_to_ids, fixture_prefix="related_to/")

    people_ids = []
    if "people" in include:
        people_ids = ["garjbvhe", "vc6xrky5"]
        add_mock_responses_for_ids(people_ids, fixture_prefix="people/")

    add_mock_related([MOCK_CONCEPT_ID], broader_than_ids, BROADER_THAN_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], related_to_ids, RELATED_TO_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], people_ids, PEOPLE_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], [], FIELDS_OF_WORK_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], [], NARROWER_THAN_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], [], FREQUENT_COLLABORATORS_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], [], RELATED_TOPICS_QUERY)
    add_mock_related([MOCK_CONCEPT_ID], [], HAS_FOUNDER_QUERY)


def get_catalogue_concept_mock(
    include: list[ConceptRelatedQuery],
) -> IndexableConcept:
    alternative_labels = [
        "Alternative label",
        "Another alternative label",
        "Wikidata alternative label",
    ]

    broader_than = []
    if "broader_than" in include:
        broader_than = [
            ConceptRelatedTo(
                label="Electromagnetic Radiation",
                id="hstuwwsu",
                conceptType="Concept",
            ),
            ConceptRelatedTo(
                label="Wave mechanics",
                id="hv6pemej",
                conceptType="Concept",
            ),
            ConceptRelatedTo(
                label="Electric waves",
                id="ugcgqepy",
                conceptType="Concept",
            ),
        ]

    people = []
    if "people" in include:
        people = [
            ConceptRelatedTo(
                label="Bube, Richard H., 1927-",
                id="garjbvhe",
                conceptType="Person",
            ),
            ConceptRelatedTo(
                label="Tegart, W. J. McG.",
                id="vc6xrky5",
                conceptType="Person",
            ),
        ]

    related_to = []
    if "related_to" in include:
        related_to = [
            ConceptRelatedTo(
                label="Hilton, Violet, 1908-1969",
                id="tzrtx26u",
                relationshipType="has_sibling",
                conceptType="Person",
            )
        ]

    return IndexableConcept(
        query=ConceptQuery(
            id=MOCK_CONCEPT_ID,
            label="LoC label",
            type="Person",
            identifiers=[
                ConceptIdentifier(
                    value="123",
                    identifierType="lc-names",
                )
            ],
            alternativeLabels=alternative_labels,
        ),
        display=ConceptDisplay(
            id=MOCK_CONCEPT_ID,
            label="LoC label",
            displayLabel="Wikidata label",
            type="Person",
            identifiers=[
                DisplayIdentifier(
                    value="123",
                    identifierType=DisplayIdentifierType(
                        id="lc-names",
                        label="Library of Congress Name authority records",
                        type="IdentifierType",
                    ),
                )
            ],
            alternativeLabels=alternative_labels,
            description=ConceptDescription(
                text="Description",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/456",
            ),
            sameAs=[],
            displayImages=[],
            relatedConcepts=RelatedConcepts(
                relatedTo=related_to,
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=broader_than,
                people=people,
                frequentCollaborators=[],
                relatedTopics=[],
                foundedBy=[],
            ),
        ),
    )


def _get_result_s3_uri(
    result: IngestorIndexerLambdaEvent, loader_event: IngestorLoaderLambdaEvent
) -> str:
    if result.objects_to_index:
        return result.objects_to_index[0].s3_uri

    prefix = loader_event.get_path_prefix()
    uri_prefix = f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{prefix}/"

    matches = [
        uri
        for uri in MockSmartOpen.file_lookup
        if isinstance(uri, str)
        and uri.startswith(uri_prefix)
        and uri.endswith(f".{loader_event.load_format}")
    ]

    assert len(matches) == 1
    return matches[0]


def _get_report_uri(loader_event: IngestorLoaderLambdaEvent) -> str:
    return loader_event.get_s3_uri("report.loader", "json")


def _read_loader_report(loader_event: IngestorLoaderLambdaEvent) -> dict[str, Any]:
    with MockSmartOpen.open(_get_report_uri(loader_event), "r") as f:
        return cast(dict[str, Any], json.load(f))


def check_processed_concept(s3_uri: str, expected_concept: IndexableConcept) -> None:
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        assert len(df) == 1

        catalogue_concepts = [
            IndexableConcept.model_validate(row) for row in df.to_dicts()
        ]

        assert len(catalogue_concepts) == 1
        assert catalogue_concepts[0] == expected_concept


def _compare_events(
    event: IngestorIndexerLambdaEvent, expected_event: IngestorIndexerLambdaEvent
) -> None:
    event_payload = event.model_dump()
    expected_payload = expected_event.model_dump()

    event_objects = event_payload.get("objects_to_index")
    expected_objects = expected_payload.get("objects_to_index")

    if event_objects and expected_objects:
        assert len(event_objects) == len(expected_objects)
        for actual, expected in zip(event_objects, expected_objects, strict=False):
            actual["content_length"] = 0
            expected["content_length"] = 0

    assert event_payload == expected_payload


def _get_expected_metrics(
    loader_event: IngestorLoaderLambdaEvent, record_count: int, total_file_size: int
) -> list[dict]:
    dimensions = {
        "ingestor_type": loader_event.ingestor_type,
        "pipeline_date": loader_event.pipeline_date,
        "index_date": loader_event.index_date,
    }

    return [
        {
            "namespace": "catalogue_graph_pipeline",
            "value": record_count,
            "metric_name": "record_count",
            "dimensions": dimensions,
        },
        {
            "namespace": "catalogue_graph_pipeline",
            "value": total_file_size,
            "metric_name": "total_file_size",
            "dimensions": dimensions,
        },
    ]


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
def test_ingestor_loader_reports_metrics_and_writes_report(
    monkeypatch: pytest.MonkeyPatch, pass_objects_to_index: bool
) -> None:
    class DummyTransformer:
        def load_documents(
            self,
            event: IngestorLoaderLambdaEvent,
            load_destination: str,
        ) -> list[IngestorIndexerObject]:
            return [
                IngestorIndexerObject(
                    s3_uri=event.get_s3_uri("00000000-00000001"),
                    content_length=1000,
                    record_count=100,
                ),
                IngestorIndexerObject(
                    s3_uri=event.get_s3_uri("00000001-00000002"),
                    content_length=2000,
                    record_count=200,
                ),
            ]

    monkeypatch.setattr(
        "ingestor.steps.ingestor_loader.create_transformer",
        lambda event, es_mode: DummyTransformer(),
    )

    loader_event = make_loader_event(pass_objects_to_index, "concepts", "123")
    result = handler(loader_event)

    expected_metrics = _get_expected_metrics(
        loader_event, record_count=300, total_file_size=3000
    )
    assert MockCloudwatchClient.metrics_reported == expected_metrics

    report = _read_loader_report(loader_event)
    assert report["record_count"] == 300
    assert report["total_file_size"] == 3000
    assert report["ingestor_type"] == loader_event.ingestor_type
    assert report["pipeline_date"] == loader_event.pipeline_date
    assert report["index_dates"]["concepts"] == loader_event.index_date
    assert report["job_id"] == loader_event.job_id

    if pass_objects_to_index:
        assert result.objects_to_index is not None
        assert [obj.model_dump() for obj in result.objects_to_index] == [
            obj.model_dump()
            for obj in [
                IngestorIndexerObject(
                    s3_uri=loader_event.get_s3_uri("00000000-00000001"),
                    content_length=1000,
                    record_count=100,
                ),
                IngestorIndexerObject(
                    s3_uri=loader_event.get_s3_uri("00000001-00000002"),
                    content_length=2000,
                    record_count=200,
                ),
            ]
        ]
    else:
        assert result.objects_to_index is None


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
def test_ingestor_loader_no_related_concepts(pass_objects_to_index: bool) -> None:
    mock_merged_work()
    mock_neptune_responses([])

    loader_event = make_loader_event(pass_objects_to_index=pass_objects_to_index)
    result = handler(loader_event)

    expected_event = make_expected_indexer_event(
        loader_event,
        "00000000-00000001.parquet",
        record_count=1,
        include_objects=pass_objects_to_index,
    )

    # We expect a total of 11 API calls:
    # * 4 to retrieve concept data (concept query, types query, same as query, and source concepts query)
    # * 8 to retrieve related concept data (one for each related concept category, such as 'people' or 'broader than')
    _compare_events(result, expected_event)
    assert len(MockRequest.calls) == 12

    expected_concept = get_catalogue_concept_mock([])
    s3_uri = _get_result_s3_uri(result, loader_event)
    check_processed_concept(s3_uri, expected_concept)

    report = _read_loader_report(loader_event)
    assert report["total_file_size"] >= 0
    assert MockCloudwatchClient.metrics_reported == _get_expected_metrics(
        loader_event,
        record_count=report["record_count"],
        total_file_size=report["total_file_size"],
    )


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
def test_ingestor_loader_with_broader_than_concepts(
    pass_objects_to_index: bool,
) -> None:
    mock_merged_work()
    mock_neptune_responses(["broader_than"])

    loader_event = make_loader_event(pass_objects_to_index=pass_objects_to_index)
    result = handler(loader_event)

    expected_event = make_expected_indexer_event(
        loader_event,
        "00000000-00000001.parquet",
        record_count=1,
        include_objects=pass_objects_to_index,
    )

    # We expect a total of 15 API calls:
    # * 12 to retrieve the same data as the `test_ingestor_loader_no_related_concepts` test case
    # * 4 to retrieve concept data for broader than concepts
    _compare_events(result, expected_event)
    assert len(MockRequest.calls) == 16

    expected_concept = get_catalogue_concept_mock(["broader_than"])
    s3_uri = _get_result_s3_uri(result, loader_event)
    check_processed_concept(s3_uri, expected_concept)

    report = _read_loader_report(loader_event)
    assert report["record_count"] == 1


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
def test_ingestor_loader_with_related_to_concepts(
    pass_objects_to_index: bool,
) -> None:
    mock_merged_work()
    mock_neptune_responses(["related_to", "people"])

    loader_event = make_loader_event(pass_objects_to_index=pass_objects_to_index)
    result = handler(loader_event)

    expected_event = make_expected_indexer_event(
        loader_event,
        "00000000-00000001.parquet",
        record_count=1,
        include_objects=pass_objects_to_index,
    )

    # Since we're including two separate groups of related concepts, we expect 4 additional API calls
    # on top of those in `test_ingestor_loader_with_broader_than_concepts`
    _compare_events(result, expected_event)
    assert len(MockRequest.calls) == 20

    expected_concept = get_catalogue_concept_mock(["related_to", "people"])
    s3_uri = _get_result_s3_uri(result, loader_event)
    check_processed_concept(s3_uri, expected_concept)


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
def test_ingestor_loader_no_concepts_to_process(pass_objects_to_index: bool) -> None:
    loader_event = make_loader_event(pass_objects_to_index=pass_objects_to_index)
    result = handler(loader_event)

    if pass_objects_to_index:
        assert result.objects_to_index == []
    else:
        assert result.objects_to_index is None

    assert len(MockRequest.calls) == 0

    prefix = f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{loader_event.get_path_prefix()}"
    parquet_matches = [
        uri
        for uri in MockSmartOpen.file_lookup
        if isinstance(uri, str)
        and uri.startswith(prefix)
        and uri.endswith(f".{loader_event.load_format}")
    ]

    assert parquet_matches == []

    report_uri = loader_event.get_s3_uri("report.loader", "json")
    with MockSmartOpen.open(report_uri, "r") as f:
        report = json.load(f)

    assert report["record_count"] == 0
    assert report["total_file_size"] == 0
    assert report["ingestor_type"] == loader_event.ingestor_type
    assert report["pipeline_date"] == loader_event.pipeline_date
    assert report["index_dates"]["concepts"] == loader_event.index_date
    assert report["job_id"] == loader_event.job_id
    assert MockCloudwatchClient.metrics_reported == _get_expected_metrics(
        loader_event, record_count=0, total_file_size=0
    )


def test_ingestor_loader_bad_neptune_response() -> None:
    mock_merged_work()

    _add_neptune_mock_response(
        [MOCK_CONCEPT_ID], SAME_AS_CONCEPT_QUERY, [{"foo": "bar"}]
    )

    loader_event = make_loader_event(pass_objects_to_index=False)

    with pytest.raises(KeyError):
        handler(loader_event)


@pytest.mark.parametrize("pass_objects_to_index", [False, True])
@freeze_time("2025-09-09")
def test_ingestor_loader_non_visible_works(pass_objects_to_index: bool) -> None:
    # Add one of each non-visible work type
    add_mock_merged_documents(pipeline_date=MOCK_PIPELINE_DATE, work_status="Deleted")
    add_mock_merged_documents(pipeline_date=MOCK_PIPELINE_DATE, work_status="Invisible")
    add_mock_merged_documents(
        pipeline_date=MOCK_PIPELINE_DATE, work_status="Redirected"
    )

    loader_event = make_loader_event(
        pass_objects_to_index=pass_objects_to_index, ingestor_type="works"
    )
    expected_indexer_event = make_expected_indexer_event(
        loader_event,
        "00000000-00000003.parquet",
        record_count=3,
        include_objects=pass_objects_to_index,
    )

    result = handler(loader_event)

    _compare_events(result, expected_indexer_event)
    assert len(MockRequest.calls) == 0

    # The dataframe should have all three works
    s3_uri = _get_result_s3_uri(result, loader_event)
    with MockSmartOpen.open(s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        items = df.to_dicts()

    assert len(items) == 3
    deleted_work = [i for i in items if i["type"] == "Deleted"][0]
    invisible_work = [i for i in items if i["type"] == "Invisible"][0]
    redirected_work = [i for i in items if i["type"] == "Redirected"][0]

    # Time is frozen in local timezone, convert_datetime_to_utc_iso will handle conversion
    now = datetime.now()
    now_iso = convert_datetime_to_utc_iso(now)

    assert DeletedIndexableWork(**deleted_work) == DeletedIndexableWork(
        debug=DeletedWorkDebug(
            source=WorkDebugSource(
                id="fz655hx4",
                identifier=SourceIdentifier(
                    identifier_type=Id(id="sierra-system-number"),
                    ontology_type="Work",
                    value="b15610512",
                ),
                version=20,
                modified_time="2025-10-09T12:32:42Z",
            ),
            merged_time="2025-10-09T12:35:31.612637Z",
            indexed_time=now_iso,
            deleted_reason=DeletedReason(info="Sierra", type="SuppressedFromSource"),
            merge_candidates=[],
        ),
        type="Deleted",
    )

    assert InvisibleIndexableWork(**invisible_work) == InvisibleIndexableWork(
        debug=InvisibleWorkDebug(
            source=WorkDebugSource(
                id="sghsneca",
                identifier=SourceIdentifier(
                    identifier_type=Id(id="mets"),
                    ontology_type="Work",
                    value="b32717714",
                ),
                version=1,
                modified_time="2022-05-23T15:50:41.008Z",
            ),
            merged_time="2025-10-08T15:31:52.203950Z",
            indexed_time=now_iso,
            invisibility_reasons=[InvisibleReason(type="MetsWorksAreNotVisible")],
            merge_candidates=[
                MergeCandidate(
                    id=Identified(
                        canonical_id="avwk5k79",
                        source_identifier=SourceIdentifier(
                            identifier_type=Id(id="sierra-system-number"),
                            ontology_type="Work",
                            value="b32717714",
                        ),
                        other_identifiers=[],
                    ),
                    reason="METS work",
                )
            ],
        ),
        type="Invisible",
    )

    assert RedirectedIndexableWork(**redirected_work) == RedirectedIndexableWork(
        debug=RedirectedWorkDebug(
            source=WorkDebugSource(
                id="cbgkvkx5",
                identifier=SourceIdentifier(
                    identifier_type=Id(id="mets"),
                    ontology_type="Work",
                    value="b18029048",
                ),
                version=2,
                modified_time="2025-10-09T11:41:53.596657Z",
            ),
            merged_time="2025-10-09T12:09:04.086557Z",
            indexed_time=now_iso,
            merge_candidates=[
                MergeCandidate(
                    id=Identified(
                        canonical_id="qcp6bq89",
                        source_identifier=SourceIdentifier(
                            identifier_type=Id(id="sierra-system-number"),
                            ontology_type="Work",
                            value="b18029048",
                        ),
                        other_identifiers=[],
                    ),
                    reason="METS work",
                )
            ],
        ),
        redirect_target=Identified(
            canonical_id="p5w7ujap",
            source_identifier=SourceIdentifier(
                identifier_type=Id(id="sierra-system-number"),
                ontology_type="Work",
                value="b1206094x",
            ),
            other_identifiers=[],
        ),
        type="Redirected",
    )
