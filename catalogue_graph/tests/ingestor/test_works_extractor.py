from typing import Any, Literal
from unittest.mock import patch

import pytest
from test_mocks import MockElasticsearchClient, add_neptune_mock_response
from test_utils import load_json_fixture

from ingestor.extractors.works_extractor import ExtractedWork, GraphWorksExtractor
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
    WORK_QUERY,
)

DENORMALISED_FIXTURE = load_json_fixture("ingestor/single_denormalised.json")
ANCESTORS_FIXTURE = load_json_fixture("neptune/work_ancestors_single.json")
CHILDREN_FIXTURE = load_json_fixture("neptune/work_children_single.json")
CONCEPTS_FIXTURE = load_json_fixture("neptune/work_concepts_single.json")

EXPECTED_NEPTUNE_PARAMS = {"start_offset": 0, "limit": 10}


def mock_es_work(work_id: str) -> None:
    MockElasticsearchClient.index(
        "works-denormalised-dev", work_id, DENORMALISED_FIXTURE
    )


def mock_work_ids(ids: list[str]) -> None:
    neptune_ids = [{"id": _id} for _id in ids]
    add_neptune_mock_response(WORK_QUERY, EXPECTED_NEPTUNE_PARAMS, neptune_ids)


def mock_graph_relationships(
    work_id: str, include: list[Literal["ancestors", "children", "concepts"]]
) -> None:
    ancestors, children, concepts = [], [], []
    if "ancestors" in include:
        ancestors = [{"id": work_id, "ancestor_works": ANCESTORS_FIXTURE}]
    if "children" in include:
        children = [{"id": work_id, "children": CHILDREN_FIXTURE}]
    if "concepts" in include:
        concepts = [{"id": work_id, "concepts": CONCEPTS_FIXTURE}]

    add_neptune_mock_response(WORK_ANCESTORS_QUERY, EXPECTED_NEPTUNE_PARAMS, ancestors)
    add_neptune_mock_response(WORK_CHILDREN_QUERY, EXPECTED_NEPTUNE_PARAMS, children)
    add_neptune_mock_response(WORK_CONCEPTS_QUERY, EXPECTED_NEPTUNE_PARAMS, concepts)


def test_with_ancestors() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    mock_work_ids(["a24esypq"])
    mock_graph_relationships("a24esypq", ["ancestors", "children"])
    MockElasticsearchClient.index(
        "works-denormalised-dev", "a24esypq", DENORMALISED_FIXTURE
    )

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=DENORMALISED_FIXTURE,
        hierarchy=WorkHierarchy(
            id="a24esypq", ancestor_works=ANCESTORS_FIXTURE, children=CHILDREN_FIXTURE
        ),
        concepts=[],
    )


def test_with_concepts() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    mock_work_ids(["a24esypq"])
    mock_graph_relationships("a24esypq", ["concepts"])
    mock_es_work("a24esypq")

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=DENORMALISED_FIXTURE,
        hierarchy=WorkHierarchy(id="a24esypq", ancestor_works=[], children=[]),
        concepts=CONCEPTS_FIXTURE,
    )


def test_without_graph_relationships() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    mock_work_ids(["a24esypq"])
    mock_graph_relationships("a24esypq", [])
    mock_es_work("a24esypq")

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=DENORMALISED_FIXTURE,
        hierarchy=WorkHierarchy(id="a24esypq", ancestor_works=[], children=[]),
        concepts=[],
    )


def test_missing_in_denormalised() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    mock_work_ids(["a24esypq"])
    mock_graph_relationships("a24esypq", ["concepts", "ancestors", "children"])

    # Items which exist in the catalogue graph but do not exist in the denormalised index should not be extracted
    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 0


def test_multiple_works() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    # Add three works to the catalogue graph
    ids = ["123", "456", "789"]
    mock_work_ids(ids)

    # Add mock graph relationships to one of the works
    mock_graph_relationships("456", ["concepts", "ancestors", "children"])

    # Only include two of the works in the denormalised index
    for work_id in ["123", "456"]:
        mock_es_work(work_id)

    expected_results = [
        ExtractedWork(
            work=DENORMALISED_FIXTURE,
            hierarchy=WorkHierarchy(id="123", ancestor_works=[], children=[]),
            concepts=[],
        ),
        ExtractedWork(
            work=DENORMALISED_FIXTURE,
            hierarchy=WorkHierarchy(
                id="456", ancestor_works=ANCESTORS_FIXTURE, children=CHILDREN_FIXTURE
            ),
            concepts=CONCEPTS_FIXTURE,
        ),
    ]

    extracted_items = list(extractor.extract_raw())
    for result in expected_results:
        assert result in extracted_items


def test_elasticsearch_error() -> None:
    extractor = GraphWorksExtractor("dev", 0, 10, False)

    mock_work_ids(["123", "456", "789"])
    mock_graph_relationships("456", [])

    def mock_mget_error(_: Any, index: str, body: dict) -> dict:
        return {"docs": [{"_id": "456", "error": {"some_key": "Failed to extract."}}]}

    with (
        patch.object(MockElasticsearchClient, "mget", mock_mget_error),
        pytest.raises(ValueError),
    ):
        list(extractor.extract_raw())
