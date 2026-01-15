"""Integration tests for Neptune graph queries.

These tests use the live database, so they are marked as `integration` and
deselected by default in pytest config.
"""

import json
import warnings
from functools import lru_cache
from pathlib import Path
from typing import Any

import pytest
from pydantic import BaseModel

from clients.base_neptune_client import BaseNeptuneClient
from ingestor.extractors.concepts_extractor import CONCEPT_QUERY_PARAMS
from ingestor.queries.concept_queries import (
    BROADER_THAN_QUERY,
    CONCEPT_TYPE_QUERY,
    FIELDS_OF_WORK_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    HAS_FOUNDER_QUERY,
    NARROWER_THAN_QUERY,
    PEOPLE_QUERY,
    RELATED_TO_QUERY,
    RELATED_TOPICS_QUERY,
    SAME_AS_CONCEPT_QUERY,
)
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
)
from utils.aws import get_neptune_client

pytestmark = pytest.mark.integration


@lru_cache(maxsize=1)
def neptune_client() -> BaseNeptuneClient:
    return get_neptune_client(True)


def load_json_fixture(file_name: str) -> Any:
    path = Path(__file__).parent / "fixtures" / f"{file_name}.json"
    with path.open() as f:
        return json.loads(f.read())


MIN_MATCH_RATIO = 0.9

WORK_ANCESTORS_BY_WORK_ID = load_json_fixture("work_ancestors_by_work_id")
WORK_IDS_WITHOUT_ANCESTORS = load_json_fixture("work_ids_without_ancestors")

CONCEPT_SAME_AS_BY_CONCEPT_ID = load_json_fixture("concept_same_as_by_concept_id")
CONCEPT_IDS_WITHOUT_SAME_AS = load_json_fixture("concept_ids_without_same_as")

CONCEPT_TYPES_BY_CONCEPT_ID = load_json_fixture("concept_types_by_concept_id")

CONCEPT_RELATED_TO_BY_CONCEPT_ID = load_json_fixture("concept_related_to_by_concept_id")
CONCEPT_IDS_WITHOUT_RELATED_TO = load_json_fixture("concept_ids_without_related_to")

CONCEPT_FREQUENT_COLLABORATORS_BY_CONCEPT_ID = load_json_fixture(
    "concept_frequent_collaborators_by_concept_id"
)
CONCEPT_IDS_WITHOUT_FREQUENT_COLLABORATORS = load_json_fixture(
    "concept_ids_without_frequent_collaborators"
)

CONCEPT_RELATED_TOPICS_BY_CONCEPT_ID = load_json_fixture(
    "concept_related_topics_by_concept_id"
)
CONCEPT_IDS_WITHOUT_RELATED_TOPICS = load_json_fixture(
    "concept_ids_without_related_topics"
)

CONCEPT_FIELDS_OF_WORK_BY_CONCEPT_ID = load_json_fixture(
    "concept_fields_of_work_by_concept_id"
)
CONCEPT_IDS_WITHOUT_FIELDS_OF_WORK = load_json_fixture(
    "concept_ids_without_fields_of_work"
)

CONCEPT_NARROWER_THAN_BY_CONCEPT_ID = load_json_fixture(
    "concept_narrower_than_by_concept_id"
)
CONCEPT_IDS_WITHOUT_NARROWER_THAN = load_json_fixture(
    "concept_ids_without_narrower_than"
)

CONCEPT_BROADER_THAN_BY_CONCEPT_ID = load_json_fixture(
    "concept_broader_than_by_concept_id"
)
CONCEPT_IDS_WITHOUT_BROADER_THAN = load_json_fixture("concept_ids_without_broader_than")

CONCEPT_PEOPLE_BY_CONCEPT_ID = load_json_fixture("concept_people_by_concept_id")
CONCEPT_IDS_WITHOUT_PEOPLE = load_json_fixture("concept_ids_without_people")

CONCEPT_HAS_FOUNDER_BY_CONCEPT_ID = load_json_fixture(
    "concept_has_founder_by_concept_id"
)
CONCEPT_IDS_WITHOUT_HAS_FOUNDER = load_json_fixture("concept_ids_without_has_founder")


class GraphQueryTest(BaseModel):
    query: str
    expected_results: dict[str, list[Any]]

    @property
    def ids(self) -> list[str]:
        return list(self.expected_results.keys())

    def run(self) -> None:
        query_params = {"ids": self.ids, **CONCEPT_QUERY_PARAMS}
        response = neptune_client().run_open_cypher_query(self.query, query_params)
        response_by_id = {item["id"]: item for item in response}

        mismatches_returned: dict[str, Any] = {}
        mismatches_expected: dict[str, Any] = {}

        for item_id, expected_item in self.expected_results.items():
            raw_returned_item = response_by_id.get(item_id)
            if raw_returned_item is None:
                mismatches_expected[item_id] = expected_item
                mismatches_returned[item_id] = None
                continue

            returned_item = self.extract_single(raw_returned_item)
            if not self.compare_single(expected_item, returned_item):
                mismatches_returned[item_id] = returned_item
                mismatches_expected[item_id] = expected_item

        matched_count = len(self.ids) - len(mismatches_returned)
        matched_ratio = matched_count / len(self.ids)

        if matched_ratio < MIN_MATCH_RATIO:
            message = (
                f"{self.__class__.__name__} matched {matched_count}/{len(self.ids)} "
                f"({matched_ratio:.2%}) below threshold {MIN_MATCH_RATIO:.0%}."
            )
            assert mismatches_returned == mismatches_expected, message

        self._show_warnings(mismatches_expected, mismatches_returned)

    def _show_warnings(self, expected: dict, returned: dict) -> None:
        for item_id, expected_item in expected.items():
            returned_item = returned[item_id]

            if returned_item is None:
                warnings.warn(
                    f"Missing result for id '{item_id}' in {self.__class__.__name__}.",
                    stacklevel=2,
                )
            else:
                warnings.warn(
                    f"Mismatch for id '{item_id}' in {self.__class__.__name__} "
                    f"(received {returned_item} instead of {expected_item}).",
                    stacklevel=2,
                )

    def extract_single(self, raw_returned_data: Any) -> Any:
        raise NotImplementedError()

    def compare_single(self, expected_data: Any, returned_data: Any) -> bool:
        return bool(expected_data == returned_data)


class WorkAncestorsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return [a["work"]["~id"] for a in raw_returned_data["ancestors"]]


class SameAsConceptsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        same_as: list[str] = raw_returned_data["same_as_ids"]
        return same_as

    def compare_single(
        self, expected_data: list[str], returned_data: list[str]
    ) -> bool:
        expected_set = set(expected_data)
        returned_set = set(returned_data)

        diff = expected_set ^ returned_set
        union_size = len(expected_set | returned_set) or 1
        diff_ratio = len(diff) / union_size

        if diff_ratio == 0:
            return True

        if diff_ratio <= 0.2:
            warnings.warn(
                f"SameAsConceptsTest difference {diff_ratio:.0%} for ids: "
                f"missing {expected_set - returned_set}, extra {returned_set - expected_set}",
                stacklevel=2,
            )
            return True

        return False


class RelatedConceptsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return [c["id"] for c in raw_returned_data["related"]]

    def compare_single(
        self, expected_data: list[str], returned_data: list[str]
    ) -> bool:
        return sorted(expected_data) == sorted(returned_data)


class ConceptTypesTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        types: list[str] = raw_returned_data["types"]
        return types

    def compare_single(
        self, expected_data: list[str], returned_data: list[str]
    ) -> bool:
        return sorted(expected_data) == sorted(returned_data)


def assert_query_returns_no_rows(query: str, ids: list[str]) -> None:
    response = neptune_client().run_open_cypher_query(
        query, {"ids": ids, **CONCEPT_QUERY_PARAMS}
    )
    assert len(response) == 0


def test_work_ancestors() -> None:
    WorkAncestorsTest(
        query=WORK_ANCESTORS_QUERY, expected_results=WORK_ANCESTORS_BY_WORK_ID
    ).run()


def test_same_as_concepts() -> None:
    SameAsConceptsTest(
        query=SAME_AS_CONCEPT_QUERY, expected_results=CONCEPT_SAME_AS_BY_CONCEPT_ID
    ).run()


def test_concept_types() -> None:
    ConceptTypesTest(
        query=CONCEPT_TYPE_QUERY, expected_results=CONCEPT_TYPES_BY_CONCEPT_ID
    ).run()


def test_related_to_concepts() -> None:
    RelatedConceptsTest(
        query=RELATED_TO_QUERY, expected_results=CONCEPT_RELATED_TO_BY_CONCEPT_ID
    ).run()


def test_frequent_collaborator_concepts() -> None:
    RelatedConceptsTest(
        query=FREQUENT_COLLABORATORS_QUERY,
        expected_results=CONCEPT_FREQUENT_COLLABORATORS_BY_CONCEPT_ID,
    ).run()


def test_related_topics_concepts() -> None:
    RelatedConceptsTest(
        query=RELATED_TOPICS_QUERY,
        expected_results=CONCEPT_RELATED_TOPICS_BY_CONCEPT_ID,
    ).run()


def test_fields_of_work_concepts() -> None:
    RelatedConceptsTest(
        query=FIELDS_OF_WORK_QUERY,
        expected_results=CONCEPT_FIELDS_OF_WORK_BY_CONCEPT_ID,
    ).run()


def test_narrower_than_concepts() -> None:
    RelatedConceptsTest(
        query=NARROWER_THAN_QUERY,
        expected_results=CONCEPT_NARROWER_THAN_BY_CONCEPT_ID,
    ).run()


def test_broader_than_concepts() -> None:
    RelatedConceptsTest(
        query=BROADER_THAN_QUERY,
        expected_results=CONCEPT_BROADER_THAN_BY_CONCEPT_ID,
    ).run()


def test_people_concepts() -> None:
    RelatedConceptsTest(
        query=PEOPLE_QUERY,
        expected_results=CONCEPT_PEOPLE_BY_CONCEPT_ID,
    ).run()


def test_has_founder_concepts() -> None:
    RelatedConceptsTest(
        query=HAS_FOUNDER_QUERY,
        expected_results=CONCEPT_HAS_FOUNDER_BY_CONCEPT_ID,
    ).run()


def test_work_ancestors_empty_for_works_without_ancestors() -> None:
    assert_query_returns_no_rows(WORK_ANCESTORS_QUERY, WORK_IDS_WITHOUT_ANCESTORS)


def test_same_as_empty_for_concepts_without_same_as() -> None:
    assert_query_returns_no_rows(SAME_AS_CONCEPT_QUERY, CONCEPT_IDS_WITHOUT_SAME_AS)


def test_related_to_empty_for_concepts_without_related_to() -> None:
    assert_query_returns_no_rows(RELATED_TO_QUERY, CONCEPT_IDS_WITHOUT_RELATED_TO)


def test_frequent_collaborators_empty_for_concepts_without_frequent_collaborators() -> (
    None
):
    response = neptune_client().run_open_cypher_query(
        FREQUENT_COLLABORATORS_QUERY,
        {"ids": CONCEPT_IDS_WITHOUT_FREQUENT_COLLABORATORS, **CONCEPT_QUERY_PARAMS},
    )
    assert all(len(item["related"]) == 0 for item in response)


def test_related_topics_empty_for_concepts_without_related_topics() -> None:
    response = neptune_client().run_open_cypher_query(
        RELATED_TOPICS_QUERY,
        {"ids": CONCEPT_IDS_WITHOUT_RELATED_TOPICS, **CONCEPT_QUERY_PARAMS},
    )
    assert all(len(item["related"]) == 0 for item in response)


def test_fields_of_work_empty_for_concepts_without_fields_of_work() -> None:
    assert_query_returns_no_rows(
        FIELDS_OF_WORK_QUERY, CONCEPT_IDS_WITHOUT_FIELDS_OF_WORK
    )


def test_narrower_than_empty_for_concepts_without_narrower_than() -> None:
    assert_query_returns_no_rows(NARROWER_THAN_QUERY, CONCEPT_IDS_WITHOUT_NARROWER_THAN)


def test_broader_than_empty_for_concepts_without_broader_than() -> None:
    assert_query_returns_no_rows(BROADER_THAN_QUERY, CONCEPT_IDS_WITHOUT_BROADER_THAN)


def test_people_empty_for_concepts_without_people() -> None:
    assert_query_returns_no_rows(PEOPLE_QUERY, CONCEPT_IDS_WITHOUT_PEOPLE)


def test_has_founder_empty_for_concepts_without_has_founder() -> None:
    assert_query_returns_no_rows(HAS_FOUNDER_QUERY, CONCEPT_IDS_WITHOUT_HAS_FOUNDER)
