from enum import Enum, auto

import polars as pl
import pytest
from test_mocks import (
    MockElasticsearchClient,
    MockRequest,
    MockSmartOpen,
    add_neptune_mock_response,
    get_mock_ingestor_indexer_event,
    get_mock_ingestor_loader_event,
    mock_es_secrets,
)
from test_utils import (
    load_json_fixture,
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
from ingestor.queries.concept_queries import (
    BROADER_THAN_QUERY,
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    FIELDS_OF_WORK_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    NARROWER_THAN_QUERY,
    PEOPLE_QUERY,
    RELATED_TO_QUERY,
    RELATED_TOPICS_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
)
from ingestor.steps.ingestor_loader import handler
from models.graph_node import Concept, SourceConcept


class MockSourceConcept(SourceConcept):
    alternative_labels: str


def mock_denormalised_work(pipeline_date: str) -> None:
    fixture = {
        "data": {
            "contributors": [
                {
                    "agent": {
                        "id": {
                            "canonicalId": "jbxfbpzq",
                        },
                        "type": "Person",
                    }
                }
            ],
        },
    }

    index_name = f"works-denormalised-{pipeline_date}"
    MockElasticsearchClient.index(index_name, "123", fixture)


class MockNeptuneResponseItem(Enum):
    CONCEPT_RELATED_TO = auto()
    CONCEPT_BROADER_THAN = auto()
    CONCEPT_PEOPLE = auto()


def get_mock_neptune_params(ids: list[str]):
    return {
        "ignored_wikidata_ids": ["Q5", "Q151885"],
        "related_to_limit": 10,
        "shared_works_count_threshold": 3,
        "ids": ids,
    }


def add_mock_concept(concepts: list[Concept]):
    response = []
    for concept in concepts:
        response.append(
            {
                "id": concept.id,
                "concept": {
                    "~id": concept.id,
                    "~labels": ["Concept"],
                    "~properties": {
                        "id": concept.id,
                        "label": concept.label,
                        "source": concept.source,
                    },
                },
            }
        )

    ids = [concept.id for concept in concepts]
    _add_neptune_mock_response(ids, CONCEPT_QUERY, response)


def add_mock_type(ids: list[str], type_map: dict[str, list]):
    # Construct the expected Neptune response
    response = []
    for concept_id, types in type_map.items():
        response.append({"id": concept_id, "types": types})

    _add_neptune_mock_response(ids, CONCEPT_TYPE_QUERY, response)


def add_mock_same_as(ids: list[str], same_as_map: dict[str, list]):
    # Construct the expected Neptune response
    response = []
    for concept_id, same_as_ids in same_as_map.items():
        response.append({"id": concept_id, "same_as_ids": [same_as_ids]})

    _add_neptune_mock_response(ids, SAME_AS_CONCEPT_QUERY, response)


def add_mock_source_concepts(
    ids: list[str],
    source_concept_map: dict[str, list],
    linked_source_concept_map: dict[str, list],
):
    def _hi(source_concept: MockSourceConcept):
        return {
            "~id": source_concept.id,
            "~labels": ["SourceConcept"],
            "~properties": {
                "id": source_concept.id,
                "source": source_concept.source,
                "label": source_concept.label,
                "alternative_labels": source_concept.alternative_labels,
                "description": source_concept.description,
            },
        }

    response = []
    for concept_id in ids:
        response.append(
            {
                "id": concept_id,
                "source_concepts": [
                    _hi(i) for i in source_concept_map.get(concept_id, [])
                ],
                "linked_source_concepts": [
                    _hi(i) for i in linked_source_concept_map.get(concept_id, [])
                ],
            }
        )

    _add_neptune_mock_response(ids, SOURCE_CONCEPT_QUERY, response)


def add_mock_related(ids: list[str], related_map: dict[str, list], query: str):
    response = []
    for concept_id, related_ids in related_map.items():
        response.append(
            {"id": concept_id, "related": [{"id": i, "count": 1} for i in related_ids]}
        )

    _add_neptune_mock_response(ids, query, response)


def _add_neptune_mock_response(
    ids: list[str], expected_query: str, mock_results: list[dict]
) -> None:
    expected_params = get_mock_neptune_params(ids)
    add_neptune_mock_response(expected_query, expected_params, mock_results)


def mock_neptune_responses(include: list[MockNeptuneResponseItem]) -> None:
    broader_than_map = {}
    related_to_map = {}
    people_map = {}

    if MockNeptuneResponseItem.CONCEPT_BROADER_THAN in include:
        broader_than_map = {"jbxfbpzq": ["hstuwwsu", "hv6pemej", "ugcgqepy"]}
        add_mock_concept(
            [
                Concept(
                    id="hstuwwsu",
                    label="Electromagnetic Radiation",
                    source="label-derived",
                ),
                Concept(id="hv6pemej", label="Wave mechanics", source="nlm-mesh"),
                Concept(id="ugcgqepy", label="Electric waves", source="nlm-mesh"),
            ]
        )
        add_mock_same_as(["hstuwwsu", "hv6pemej", "ugcgqepy"], {})
        add_mock_type(["hstuwwsu", "hv6pemej", "ugcgqepy"], {})
        add_mock_source_concepts(["hstuwwsu", "hv6pemej", "ugcgqepy"], {}, {})
    if MockNeptuneResponseItem.CONCEPT_PEOPLE in include:
        people_results = [load_json_fixture("neptune/people_query_single.json")]
    if MockNeptuneResponseItem.CONCEPT_RELATED_TO in include:
        related_to_results = [load_json_fixture("neptune/related_to_query_single.json")]

    add_mock_concept([Concept(id="jbxfbpzq", label="some concept", source="nlm-mesh")])
    add_mock_same_as(["jbxfbpzq"], {})
    add_mock_type(["jbxfbpzq"], {"jbxfbpzq": ["Concept", "Person"]})
    add_mock_source_concepts(
        ["jbxfbpzq"],
        {
            "jbxfbpzq": [
                MockSourceConcept(
                    id="123",
                    source="lc-names",
                    label="LoC label",
                    alternative_labels="Alternative label||another alternative label",
                ),
                MockSourceConcept(
                    id="456",
                    source="wikidata",
                    label="Wikidata label",
                    alternative_labels="Wikidata alternative label",
                    description="Description",
                ),
            ]
        },
        {
            "jbxfbpzq": [
                MockSourceConcept(
                    id="123",
                    source="lc-names",
                    label="LoC label",
                    alternative_labels="Alternative label||another alternative label",
                ),
            ]
        },
    )

    add_mock_related(["jbxfbpzq"], broader_than_map, BROADER_THAN_QUERY)
    add_mock_related(["jbxfbpzq"], related_to_map, RELATED_TO_QUERY)
    add_mock_related(["jbxfbpzq"], people_map, PEOPLE_QUERY)
    add_mock_related(["jbxfbpzq"], {}, FIELDS_OF_WORK_QUERY)
    add_mock_related(["jbxfbpzq"], {}, NARROWER_THAN_QUERY)
    add_mock_related(["jbxfbpzq"], {}, FREQUENT_COLLABORATORS_QUERY)
    add_mock_related(["jbxfbpzq"], {}, RELATED_TOPICS_QUERY)


def get_catalogue_concept_mock(
    include: list[MockNeptuneResponseItem],
) -> IndexableConcept:
    alternative_labels = [
        "Alternative label",
        "Another alternative label",
        "Wikidata alternative label",
    ]

    broader_than = []
    if MockNeptuneResponseItem.CONCEPT_BROADER_THAN in include:
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
    if MockNeptuneResponseItem.CONCEPT_PEOPLE in include:
        people = [
            ConceptRelatedTo(
                label="Tegart, W. J. McG.",
                id="vc6xrky5",
                conceptType="Person",
            ),
            ConceptRelatedTo(
                label="Bube, Richard H., 1927-",
                id="garjbvhe",
                conceptType="Person",
            ),
        ]

    related_to = []
    if MockNeptuneResponseItem.CONCEPT_RELATED_TO in include:
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
            id="jbxfbpzq",
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
            id="jbxfbpzq",
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
            relatedConcepts=RelatedConcepts(
                relatedTo=related_to,
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=broader_than,
                people=people,
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )


def test_ingestor_loader_no_related_concepts() -> None:
    mock_es_secrets("graph_extractor", "2025-01-01")
    mock_denormalised_work("2025-01-01")

    included_response_items = []
    expected_concept = get_catalogue_concept_mock(included_response_items)
    mock_neptune_responses(included_response_items)

    loader_event = get_mock_ingestor_loader_event("123")
    indexer_event = get_mock_ingestor_indexer_event("123", content_length=19243)
    result = handler(loader_event)

    assert result == indexer_event
    assert len(MockRequest.calls) == 11

    request = MockRequest.calls[0]
    assert request["method"] == "POST"
    assert request["url"] == "https://test-host.com:8182/openCypher"

    with MockSmartOpen.open(indexer_event.objects_to_index[0].s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        assert len(df) == 1

        catalogue_concepts = [
            IndexableConcept.model_validate(row) for row in df.to_dicts()
        ]

        assert len(catalogue_concepts) == 1
        assert catalogue_concepts[0] == expected_concept


def test_ingestor_loader_with_broader_than_concepts() -> None:
    mock_es_secrets("graph_extractor", "2025-01-01")
    mock_denormalised_work("2025-01-01")

    included_response_items = [MockNeptuneResponseItem.CONCEPT_BROADER_THAN]
    expected_concept = get_catalogue_concept_mock(included_response_items)
    mock_neptune_responses(included_response_items)

    loader_event = get_mock_ingestor_loader_event("123")
    indexer_event = get_mock_ingestor_indexer_event("123", content_length=19654)
    result = handler(loader_event)

    assert result == indexer_event
    assert len(MockRequest.calls) == 15

    with MockSmartOpen.open(indexer_event.objects_to_index[0].s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        assert len(df) == 1

        catalogue_concepts = [
            IndexableConcept.model_validate(row) for row in df.to_dicts()
        ]

        assert len(catalogue_concepts) == 1
        assert catalogue_concepts[0] == expected_concept


def test_ingestor_loader_bad_neptune_response() -> None:
    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data={"results": [{"foo": "bar"}]},
    )

    with pytest.raises(LookupError):
        event = get_mock_ingestor_loader_event("123")
        handler(event)
