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

client = get_neptune_client(True)
MATCH_THRESHOLD = 0.9


WORK_ANCESTORS_EXPECTED = {
    "c245ztkp": [],
    "se3njj23": ["c245ztkp"],
    "nw3cjz4t": ["v2e9bnfz", "c245ztkp"],
    "ry47uhb7": [
        "gwa5jepb",
        "wass4xck",
        "m6665xnx",
        "a3yjq58m",
        "gf5eteyt",
        "tg2kvjgq",
        "k4q5uy3z",
        "hz43r7re",
    ],
    "a2239muq": [],
    "qzfrfh4a": ["c8kp6w3m", "psspw62x"],
    "vqxgyy2y": ["bsd8ps3r", "e5ywuen4", "ezq2u68w", "jwj9b483"],
    "gc8e3sgt": ["aenbrgw2", "dw67bv4q"],
    "yp25bmam": ["bhyzxmn3", "rqcbn2xj", "psspw62x"],
    "cha3rma9": ["qaagvuc5", "uwraqngx", "k2fae5cz"],
    "h33evz7q": ["csae3xcf", "sn2es4yv"],
    "tdy6njc3": ["tczkfxpg", "psspw62x"],
    "fehfu8zj": ["jjw85zqn", "yc6zfu6w", "d5yvakb5"],
    "g778tahw": ["vrvy36zt"],
    "xnaurvy8": ["aenbrgw2", "dw67bv4q"],
    "xuyv6chy": ["nt9p22d7", "cgypdtn5"],
    "xudmbm3d": ["bsd8ps3r", "e5ywuen4", "ezq2u68w", "jwj9b483"],
    "fuu4wh28": ["mmxfmrs2", "evkvwnaj", "m2ckbncx"],
    "r82gffn5": ["tzsrg6mr", "p73z6t3n", "egpwuwqg", "fy2n52en"],
    "ac3x6ph2": ["xqc9qs4x"],
}


CONCEPT_SAME_AS_EXPECTED = {
    "eva7r2dw": [
        "d8z89dv6",
        "egnqcbpn",
        "vsnwvu9k",
        "bdep75ax",
        "p3gga484",
        "x9f6yrak",
        "jubdg55b",
        "htx3zj2b",
    ]
}


CONCEPT_RELATED_TO_EXPECTED = {"eva7r2dw": ["zvpgcgjv", "sjxv6uys", "kx79h6jm"]}
CONCEPT_FREQUENT_COLLABORATORS_EXPECTED = {
    "eva7r2dw": [],
    "gk2eca5r": ["m78s9aek", "ar4dsxxw", "xcmmxsp2"],
}
CONCEPT_TYPES_EXPECTED = {"eva7r2dw": ["Concept", "Subject"]}


class GraphQueryTest(BaseModel):
    query: str
    expected_results: dict[str, list[Any]]

    @computed_field
    @property
    def ids(self) -> list[str]:
        return list(self.expected_results.keys())

    def run(self) -> None:
        query_params = {"ids": self.ids, **CONCEPT_QUERY_PARAMS}
        response = client.run_open_cypher_query(self.query, query_params)
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

        for item_id, expected_item in mismatches_expected.items():
            returned_item = mismatches_returned[item_id]

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
        return expected_data == returned_data


class WorkAncestorsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return [a["work"]["~id"] for a in raw_returned_data["ancestors"]]


class SameAsConceptsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return raw_returned_data["same_as_ids"]

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        missing = set(expected_data) - set(returned_data)
        redundant = set(returned_data) - set(expected_data)
        return (
            len(missing) / len(expected_data) < 0.2
            and len(redundant) / len(expected_data) < 0.2
        )


class RelatedConceptsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return [c["id"] for c in raw_returned_data["related"]]

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        return sorted(expected_data) == sorted(returned_data)


class ConceptTypesTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return raw_returned_data["types"]

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        return sorted(expected_data) == sorted(returned_data)


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
