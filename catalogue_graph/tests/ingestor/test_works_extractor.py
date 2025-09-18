import copy
from typing import Literal

from test_mocks import MockElasticsearchClient, add_neptune_mock_response
from test_utils import load_json_fixture

from ingestor.extractors.works_extractor import ExtractedWork, GraphWorksExtractor
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
)

DENORMALISED_FIXTURE = load_json_fixture("ingestor/single_denormalised.json")
ANCESTORS_FIXTURE = load_json_fixture("neptune/work_ancestors_single.json")
CHILDREN_FIXTURE = load_json_fixture("neptune/work_children_single.json")
CONCEPTS_FIXTURE = load_json_fixture("neptune/work_concepts_single.json")


def _get_work_fixture(work_id: str) -> DenormalisedWork:
    # Adjust canonical ID in fixture
    fixture = copy.deepcopy(DENORMALISED_FIXTURE)
    fixture["state"]["canonicalId"] = work_id
    return DenormalisedWork(**fixture)


def mock_es_work(work_id: str) -> None:
    fixture = _get_work_fixture(work_id)
    MockElasticsearchClient.index(
        "works-denormalised-dev", work_id, fixture.model_dump(by_alias=True)
    )


def mock_graph_relationships(
    work_id: str,
    all_indexed_work_ids: list[str],
    include: list[Literal["ancestors", "children", "concepts"]],
) -> None:
    ancestors, children, concepts = [], [], []
    if "ancestors" in include:
        ancestors = [{"id": work_id, "ancestors": ANCESTORS_FIXTURE}]
    if "children" in include:
        children = [{"id": work_id, "children": CHILDREN_FIXTURE}]
    if "concepts" in include:
        concepts = [{"id": work_id, "concepts": CONCEPTS_FIXTURE}]

    expected_params = {"ids": all_indexed_work_ids}
    add_neptune_mock_response(WORK_ANCESTORS_QUERY, expected_params, ancestors)
    add_neptune_mock_response(WORK_CHILDREN_QUERY, expected_params, children)
    add_neptune_mock_response(WORK_CONCEPTS_QUERY, expected_params, concepts)


def test_with_ancestors() -> None:
    extractor = GraphWorksExtractor("dev", None, False)

    mock_es_work("a24esypq")
    mock_graph_relationships("a24esypq", ["a24esypq"], ["ancestors", "children"])

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=_get_work_fixture("a24esypq"),
        hierarchy=WorkHierarchy(
            id="a24esypq", ancestors=ANCESTORS_FIXTURE, children=CHILDREN_FIXTURE
        ),
        concepts=[],
    )


def test_with_concepts() -> None:
    extractor = GraphWorksExtractor("dev", None, False)

    mock_es_work("a24esypq")
    mock_graph_relationships("a24esypq", ["a24esypq"], ["concepts"])

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=_get_work_fixture("a24esypq"),
        hierarchy=WorkHierarchy(id="a24esypq", ancestors=[], children=[]),
        concepts=CONCEPTS_FIXTURE,
    )


def test_without_graph_relationships() -> None:
    extractor = GraphWorksExtractor("dev", None, False)

    mock_es_work("a24esypq")
    mock_graph_relationships("a24esypq", ["a24esypq"], [])

    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 1
    assert extracted_items[0] == ExtractedWork(
        work=_get_work_fixture("a24esypq"),
        hierarchy=WorkHierarchy(id="a24esypq", ancestors=[], children=[]),
        concepts=[],
    )


def test_missing_in_denormalised() -> None:
    extractor = GraphWorksExtractor("dev", None, False)

    mock_graph_relationships(
        "a24esypq", ["a24esypq"], ["concepts", "ancestors", "children"]
    )

    # Items which exist in the catalogue graph but do not exist in the denormalised index should not be extracted
    extracted_items = list(extractor.extract_raw())
    assert len(extracted_items) == 0


def test_multiple_works() -> None:
    extractor = GraphWorksExtractor("dev", None, False)

    for work_id in ["123", "456"]:
        mock_es_work(work_id)

    # Add mock graph relationships to one of the works
    mock_graph_relationships(
        "456", ["123", "456"], ["concepts", "ancestors", "children"]
    )

    expected_results = [
        ExtractedWork(
            work=_get_work_fixture("123"),
            hierarchy=WorkHierarchy(id="123", ancestors=[], children=[]),
            concepts=[],
        ),
        ExtractedWork(
            work=_get_work_fixture("456"),
            hierarchy=WorkHierarchy(
                id="456", ancestors=ANCESTORS_FIXTURE, children=CHILDREN_FIXTURE
            ),
            concepts=CONCEPTS_FIXTURE,
        ),
    ]

    extracted_items = list(extractor.extract_raw())
    for result in expected_results:
        assert result in extracted_items
