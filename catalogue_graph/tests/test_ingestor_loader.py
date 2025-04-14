import json
from enum import Enum, auto

import polars as pl
import pytest
from ingestor_indexer import IngestorIndexerLambdaEvent
from ingestor_loader import (
    CONCEPT_QUERY,
    REFERENCED_TOGETHER_QUERY,
    IngestorIndexerObject,
    IngestorLoaderConfig,
    IngestorLoaderLambdaEvent,
    get_related_query,
    handler,
)
from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    RelatedConcepts,
)
from test_mocks import MockRequest, MockSmartOpen

MOCK_INGESTOR_LOADER_EVENT = IngestorLoaderLambdaEvent(
    pipeline_date="2021-07-01",
    job_id="123",
    start_offset=0,
    end_index=1,
)

MOCK_INGESTOR_LOADER_CONFIG = IngestorLoaderConfig(
    loader_s3_bucket="test-bucket",
    loader_s3_prefix="test-prefix",
)

MOCK_INGESTOR_INDEXER_EVENT = IngestorIndexerLambdaEvent(
    pipeline_date="2021-07-01",
    job_id="123",
    object_to_index=IngestorIndexerObject(
        s3_uri="s3://test-bucket/test-prefix/2021-07-01/123/00000000-00000001.parquet",
        content_length=1,
        record_count=1,
    ),
)


class MockNeptuneResponseItem(Enum):
    SOURCE_ALTERNATIVE_LABELS = auto()
    CONCEPT_RELATED_TO = auto()


def get_mock_neptune_concept_query_response(include_alternative_labels: bool) -> dict:
    data: dict = {
        "results": [
            {
                "concept": {
                    "~properties": {
                        "id": "id",
                        "label": "label",
                        "type": "type",
                    }
                },
                "relationships": [],
                "source_concepts": [
                    {
                        "~properties": {
                            "id": "456",
                            "source": "lc-names",
                            "description": "description",
                        }
                    },
                    {
                        "~properties": {
                            "id": "789",
                            "source": "nlm-mesh",
                            "description": "mesh description",
                        }
                    }                    
                ],
                "linked_source_concepts": [
                    {
                        "~properties": {
                            "id": "456",
                            "source": "lc-names",
                            "description": "description",
                        }
                    }
                ],
                "same_as_concept_ids": []
            }
        ]
    }
    
    if include_alternative_labels:
        data["results"][0]["source_concepts"][0]["~properties"]["alternative_labels"] = "alternative label||another alternative label"
        data["results"][0]["source_concepts"][1]["~properties"]["alternative_labels"] = "MeSH alternative label"
    
    return data


EXPECTED_NEPTUNE_PARAMS = {"start_offset": 0, "limit": 1, "ignored_wikidata_ids": ["Q5", "Q151885"], "related_to_limit": 10}


def add_neptune_mock_response(request_data: dict, response_data: dict) -> None:
    request_data["query"] = " ".join(request_data["query"].split())

    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data=response_data,
        body=json.dumps(request_data)
    )


def mock_neptune_responses(include: list[MockNeptuneResponseItem]) -> None:
    include_alternative_labels = MockNeptuneResponseItem.SOURCE_ALTERNATIVE_LABELS in include

    add_neptune_mock_response(
        request_data = {"query": CONCEPT_QUERY, "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data=get_mock_neptune_concept_query_response(include_alternative_labels)
    )

    add_neptune_mock_response(
        request_data={"query": get_related_query("RELATED_TO"), "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data = {"results": []}
    )

    add_neptune_mock_response(
        request_data={"query": get_related_query("HAS_FIELD_OF_WORK"), "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data={"results": []}
    )

    add_neptune_mock_response(
        request_data={"query": get_related_query("NARROWER_THAN|HAS_PARENT", 'left'), "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data = {"results": []}
    )

    add_neptune_mock_response(
        request_data={"query": get_related_query("HAS_FIELD_OF_WORK",'left'), "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data = {"results": []}
    )

    add_neptune_mock_response(
        request_data={"query": get_related_query("NARROWER_THAN"), "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data = {"results": []}
    )

    add_neptune_mock_response(
        request_data={"query": REFERENCED_TOGETHER_QUERY, "parameters": EXPECTED_NEPTUNE_PARAMS},
        response_data = {"results": []}
    )


def get_catalogue_concept_mock(include_alternative_labels: bool) -> CatalogueConcept:
    alternative_labels = []
    if include_alternative_labels:
        alternative_labels = ["Alternative label", "Another alternative label", "MeSH alternative label"]
        
    return CatalogueConcept(
        id="id",
        label="label",
        type="type",
        alternativeLabels=alternative_labels,
        description="Mesh description",
        identifiers=[
            CatalogueConceptIdentifier(
                value="456",
                identifierType="lc-names",
            )
        ],
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo = [],
            fieldsOfWork = [],
            narrowerThan = [],
            broaderThan = [],
            people = [],
            referencedTogether = []
        )
    )


def build_test_matrix() -> list[tuple]:
    return [
        (
            "happy path, with alternative labels",
            [MockNeptuneResponseItem.SOURCE_ALTERNATIVE_LABELS],
            get_catalogue_concept_mock(True)
        ),
        (
            "happy path, with NO alternative labels",
            [],
            get_catalogue_concept_mock(False)
        ),
    ]


@pytest.mark.parametrize(
    "description,included_response_items,expected_concept",
    build_test_matrix(),
    ids=lambda argvalue: argvalue,
)
def test_ingestor_loader(
    description: str,
    included_response_items: list[MockNeptuneResponseItem],
    expected_concept: CatalogueConcept,
) -> None:
    mock_neptune_responses(included_response_items)
    
    result = handler(MOCK_INGESTOR_LOADER_EVENT, MOCK_INGESTOR_LOADER_CONFIG)

    assert result == MOCK_INGESTOR_INDEXER_EVENT
    assert len(MockRequest.calls) == 7

    request = MockRequest.calls[0]
    assert request["method"] == "POST"
    assert request["url"] == "https://test-host.com:8182/openCypher"

    with MockSmartOpen.open(MOCK_INGESTOR_INDEXER_EVENT.object_to_index.s3_uri, "rb") as f:
        df = pl.read_parquet(f)
        assert len(df) == 1

        catalogue_concepts = [
            CatalogueConcept.model_validate(row) for row in df.to_dicts()
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
        handler(MOCK_INGESTOR_LOADER_EVENT, MOCK_INGESTOR_LOADER_CONFIG)

