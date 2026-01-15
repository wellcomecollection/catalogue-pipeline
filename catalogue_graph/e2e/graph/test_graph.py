import json
import os
import warnings
from typing import Any

from pydantic import BaseModel, computed_field

from ingestor.extractors.concepts_extractor import CONCEPT_QUERY_PARAMS
from ingestor.queries.concept_queries import (
    CONCEPT_TYPE_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    RELATED_TO_QUERY,
    SAME_AS_CONCEPT_QUERY,
)
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
)
from utils.aws import get_neptune_client

NEPTUNE_CLIENT = get_neptune_client(True)
MATCH_THRESHOLD = 0.9


def load_json_fixture(file_name: str) -> Any:
    path = f"{os.path.dirname(__file__)}/data/{file_name}"
    with open(path) as f:
        return json.loads(f.read())


WORK_ANCESTORS_EXPECTED = load_json_fixture("works_ancestors.json")
WORK_NO_ANCESTORS = load_json_fixture("works_ancestors_none.json")
CONCEPT_SAME_AS_EXPECTED = load_json_fixture("concepts_same_as.json")
CONCEPT_NO_SAME_AS = load_json_fixture("concepts_same_as_none.json")
CONCEPT_TYPES_EXPECTED = load_json_fixture("concepts_types.json")
CONCEPT_RELATED_TO_EXPECTED = load_json_fixture("concepts_related_to.json")
CONCEPT_NO_RELATED_TO = load_json_fixture("concepts_related_to_none.json")
CONCEPT_FREQUENT_COLLABORATORS_EXPECTED = load_json_fixture(
    "concepts_frequent_collaborators.json"
)
CONCEPT_NO_FREQUENT_COLLABORATORS = load_json_fixture(
    "concepts_frequent_collaborators_none.json"
)


class GraphQueryTest(BaseModel):
    query: str
    expected_results: dict[str, list[Any]]

    @computed_field
    @property
    def ids(self) -> list[str]:
        return list(self.expected_results.keys())

    def run(self) -> None:
        query_params = {"ids": self.ids, **CONCEPT_QUERY_PARAMS}
        response = NEPTUNE_CLIENT.run_open_cypher_query(self.query, query_params)
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

        if matched_ratio < MATCH_THRESHOLD:
            message = (
                f"{self.__class__.__name__} matched {matched_count}/{len(self.ids)} "
                f"({matched_ratio:.2%}) below threshold {MATCH_THRESHOLD:.0%}."
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

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
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

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        return sorted(expected_data) == sorted(returned_data)


class ConceptTypesTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        types: list[str] = raw_returned_data["types"]
        return types

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        return sorted(expected_data) == sorted(returned_data)


def assert_empty_response(query: str, ids: list[str]) -> None:
    response = NEPTUNE_CLIENT.run_open_cypher_query(
        query, {"ids": ids, **CONCEPT_QUERY_PARAMS}
    )
    assert len(response) == 0


def test_work_ancestors() -> None:
    WorkAncestorsTest(
        query=WORK_ANCESTORS_QUERY, expected_results=WORK_ANCESTORS_EXPECTED
    ).run()


def test_same_as_concepts() -> None:
    SameAsConceptsTest(
        query=SAME_AS_CONCEPT_QUERY, expected_results=CONCEPT_SAME_AS_EXPECTED
    ).run()


def test_concept_types() -> None:
    ConceptTypesTest(
        query=CONCEPT_TYPE_QUERY, expected_results=CONCEPT_TYPES_EXPECTED
    ).run()


def test_related_to_concepts() -> None:
    RelatedConceptsTest(
        query=RELATED_TO_QUERY, expected_results=CONCEPT_RELATED_TO_EXPECTED
    ).run()


def test_frequent_collaborator_concepts() -> None:
    RelatedConceptsTest(
        query=FREQUENT_COLLABORATORS_QUERY,
        expected_results=CONCEPT_FREQUENT_COLLABORATORS_EXPECTED,
    ).run()


def test_work_no_ancestors() -> None:
    assert_empty_response(WORK_ANCESTORS_QUERY, WORK_NO_ANCESTORS)


def test_no_same_as_concepts() -> None:
    assert_empty_response(SAME_AS_CONCEPT_QUERY, CONCEPT_NO_SAME_AS)


def test_no_related_to_concepts() -> None:
    assert_empty_response(RELATED_TO_QUERY, CONCEPT_NO_RELATED_TO)


def test_no_frequent_collaborators() -> None:
    response = NEPTUNE_CLIENT.run_open_cypher_query(
        FREQUENT_COLLABORATORS_QUERY,
        {"ids": CONCEPT_NO_FREQUENT_COLLABORATORS, **CONCEPT_QUERY_PARAMS},
    )
    assert all(len(item["related"]) == 0 for item in response)
