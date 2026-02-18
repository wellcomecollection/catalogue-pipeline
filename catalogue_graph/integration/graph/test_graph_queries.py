"""Integration tests for Neptune graph queries.

These tests use the live database, so they are marked as `integration` and
deselected by default in pytest config.

Usage:
    AWS_PROFILE=platform-developer uv run pytest -m "integration"
"""

import json
import warnings
from functools import cache, lru_cache
from pathlib import Path
from typing import Any, NamedTuple

import pytest
from clients.base_neptune_client import BaseNeptuneClient
from pydantic import BaseModel

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

# Add the 'integration' marker to ensure that integration tests are not included in regular unit test runs.
pytestmark = pytest.mark.integration


@lru_cache(maxsize=1)
def neptune_client() -> BaseNeptuneClient:
    return get_neptune_client(use_public_endpoint=True)


@cache
def load_json_fixture(name: str) -> Any:
    path = Path(__file__).parent / "fixtures" / f"{name}.json"
    return json.loads(path.read_text())


# The graph is not static (e.g. works/concepts can be deleted). Small divergence over time is expected,
# so we allow a tolerance threshold and only fail when drift becomes significant.
# All mismatches are still logged as warnings for visibility.
MIN_MATCH_RATIO = 0.9


class GraphQueryTest(BaseModel):
    query: str
    expected_results: dict[str, list[Any]]

    @property
    def ids(self) -> list[str]:
        return list(self.expected_results.keys())

    def run(self) -> None:
        # Run the selected query against the live graph using the fixture IDs.
        query_params = {"ids": self.ids, **CONCEPT_QUERY_PARAMS}
        response = neptune_client().run_open_cypher_query(self.query, query_params)
        response_by_id = {item["id"]: item for item in response}

        # Compare each fixture item to the corresponding row returned by Neptune.
        mismatches_returned: dict[str, Any] = {}
        mismatches_expected: dict[str, Any] = {}

        for item_id, expected_item in self.expected_results.items():
            # Record mismatches either when Neptune returns no row for an ID, or when
            # the returned row (after extraction) doesn't match the fixture.
            raw_returned_item = response_by_id.get(item_id)
            if raw_returned_item is None:
                mismatches_expected[item_id] = expected_item
                mismatches_returned[item_id] = None
                continue

            returned_item = self.extract_single(raw_returned_item)
            if not self.compare_single(expected_item, returned_item):
                mismatches_returned[item_id] = returned_item
                mismatches_expected[item_id] = expected_item

        # Fail only when drift exceeds the configured threshold.
        matched_count = len(self.ids) - len(mismatches_returned)
        matched_ratio = matched_count / len(self.ids)

        if matched_ratio < MIN_MATCH_RATIO:
            message = (
                f"{self.__class__.__name__} matched {matched_count}/{len(self.ids)} "
                f"({matched_ratio:.2%}) below threshold {MIN_MATCH_RATIO:.0%}."
            )
            assert mismatches_returned == mismatches_expected, message

        # Otherwise, emit warnings for any mismatches.
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
        """
        Take a raw item returned by Neptune (e.g. a row containing `related` concepts)
        and return the specific data we want to compare against the JSON fixture
        (e.g. a list of related concept IDs).
        """
        raise NotImplementedError()

    def compare_single(self, expected_data: Any, returned_data: Any) -> bool:
        """
        Compare a fixture value to the corresponding value extracted from Neptune.

        Subclasses can override this to apply case-specific normalisation before
        comparing (e.g. applying tolerance thresholds, ignoring ordering).
        """
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
                f"SameAsConceptsTest difference {diff_ratio:.0%}. "
                f"Missing IDs: {list(expected_set - returned_set)}. Extra IDs: {list(returned_set - expected_set)}",
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


class MatchCase(NamedTuple):
    name: str
    query: str
    test_cls: type[GraphQueryTest]
    expected_fixture: str


MATCH_CASES: list[MatchCase] = [
    MatchCase(
        name="work_ancestors",
        query=WORK_ANCESTORS_QUERY,
        test_cls=WorkAncestorsTest,
        expected_fixture="work_ancestors_by_work_id",
    ),
    MatchCase(
        name="concept_same_as",
        query=SAME_AS_CONCEPT_QUERY,
        test_cls=SameAsConceptsTest,
        expected_fixture="concept_same_as_by_concept_id",
    ),
    MatchCase(
        name="concept_types",
        query=CONCEPT_TYPE_QUERY,
        test_cls=ConceptTypesTest,
        expected_fixture="concept_types_by_concept_id",
    ),
    MatchCase(
        name="concept_related_to",
        query=RELATED_TO_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_related_to_by_concept_id",
    ),
    MatchCase(
        name="concept_frequent_collaborators",
        query=FREQUENT_COLLABORATORS_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_frequent_collaborators_by_concept_id",
    ),
    MatchCase(
        name="concept_related_topics",
        query=RELATED_TOPICS_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_related_topics_by_concept_id",
    ),
    MatchCase(
        name="concept_fields_of_work",
        query=FIELDS_OF_WORK_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_fields_of_work_by_concept_id",
    ),
    MatchCase(
        name="concept_narrower_than",
        query=NARROWER_THAN_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_narrower_than_by_concept_id",
    ),
    MatchCase(
        name="concept_broader_than",
        query=BROADER_THAN_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_broader_than_by_concept_id",
    ),
    MatchCase(
        name="concept_people",
        query=PEOPLE_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_people_by_concept_id",
    ),
    MatchCase(
        name="concept_has_founder",
        query=HAS_FOUNDER_QUERY,
        test_cls=RelatedConceptsTest,
        expected_fixture="concept_has_founder_by_concept_id",
    ),
]


class EmptyCase(NamedTuple):
    name: str
    query: str
    empty_fixture: str


# These queries should return *zero rows* for the provided IDs.
NO_ROWS_EMPTY_CASES: list[EmptyCase] = [
    EmptyCase(
        name="work_ancestors",
        query=WORK_ANCESTORS_QUERY,
        empty_fixture="work_ids_without_ancestors",
    ),
    EmptyCase(
        name="concept_same_as",
        query=SAME_AS_CONCEPT_QUERY,
        empty_fixture="concept_ids_without_same_as",
    ),
    EmptyCase(
        name="concept_related_to",
        query=RELATED_TO_QUERY,
        empty_fixture="concept_ids_without_related_to",
    ),
    EmptyCase(
        name="concept_fields_of_work",
        query=FIELDS_OF_WORK_QUERY,
        empty_fixture="concept_ids_without_fields_of_work",
    ),
    EmptyCase(
        name="concept_narrower_than",
        query=NARROWER_THAN_QUERY,
        empty_fixture="concept_ids_without_narrower_than",
    ),
    EmptyCase(
        name="concept_broader_than",
        query=BROADER_THAN_QUERY,
        empty_fixture="concept_ids_without_broader_than",
    ),
    EmptyCase(
        name="concept_people",
        query=PEOPLE_QUERY,
        empty_fixture="concept_ids_without_people",
    ),
    EmptyCase(
        name="concept_has_founder",
        query=HAS_FOUNDER_QUERY,
        empty_fixture="concept_ids_without_has_founder",
    ),
]


# These queries still return rows, but with `related=[]`.
EMPTY_RELATED_LIST_CASES: list[EmptyCase] = [
    EmptyCase(
        name="concept_frequent_collaborators",
        query=FREQUENT_COLLABORATORS_QUERY,
        empty_fixture="concept_ids_without_frequent_collaborators",
    ),
    EmptyCase(
        name="concept_related_topics",
        query=RELATED_TOPICS_QUERY,
        empty_fixture="concept_ids_without_related_topics",
    ),
]


@pytest.mark.parametrize("case", MATCH_CASES, ids=lambda c: c.name)
def test_graph_query_matches_fixture(case: MatchCase) -> None:
    expected_results = load_json_fixture(case.expected_fixture)
    case.test_cls(query=case.query, expected_results=expected_results).run()


@pytest.mark.parametrize("case", NO_ROWS_EMPTY_CASES, ids=lambda c: f"{c.name}_empty")
def test_graph_query_returns_no_rows_for_known_empty_ids(case: EmptyCase) -> None:
    ids = load_json_fixture(case.empty_fixture)
    response = neptune_client().run_open_cypher_query(
        case.query, {"ids": ids, **CONCEPT_QUERY_PARAMS}
    )
    assert len(response) == 0


@pytest.mark.parametrize(
    "case",
    EMPTY_RELATED_LIST_CASES,
    ids=lambda c: f"{c.name}_empty_related",
)
def test_graph_query_returns_empty_related_list_for_known_empty_ids(
    case: EmptyCase,
) -> None:
    ids = load_json_fixture(case.empty_fixture)
    response = neptune_client().run_open_cypher_query(
        case.query, {"ids": ids, **CONCEPT_QUERY_PARAMS}
    )
    assert all(len(item["related"]) == 0 for item in response)
