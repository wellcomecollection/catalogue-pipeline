from typing import Literal

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

expected_params = {"start_offset": 0, "limit": 10}


def mock_work_ids(ids: list[str]) -> None:
    neptune_ids = [{"id": _id} for _id in ids]
    add_neptune_mock_response(WORK_QUERY, expected_params, neptune_ids)


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

    add_neptune_mock_response(WORK_ANCESTORS_QUERY, expected_params, ancestors)
    add_neptune_mock_response(WORK_CHILDREN_QUERY, expected_params, children)
    add_neptune_mock_response(WORK_CONCEPTS_QUERY, expected_params, concepts)


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
    MockElasticsearchClient.index(
        "works-denormalised-dev", "a24esypq", DENORMALISED_FIXTURE
    )

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
    MockElasticsearchClient.index(
        "works-denormalised-dev", "a24esypq", DENORMALISED_FIXTURE
    )

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
