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
    "gvszk9ph": ["atvgf7zm", "xy4mm5wn"],
    "e9md8mg4": ["atvgf7zm", "xy4mm5wn"],
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
    ],
    "aue34u9q": ["dwxjjnjb", "sqdeu5c6"],
    "pss2spcc": ["ry8rk34b", "gbxcu7cs", "yjtke7a7"],
    "xdndcdz7": [
        "fawjawyj",
        "zxw29fhe",
        "p23kcrz6",
        "dfktgwen",
        "kbfa5mwb",
        "bhdxd3px",
    ],
    "dmme4rh6": [
        "yykpte9u",
        "a9f9f6vc",
        "u22tn2x8",
        "g5wp4wnb",
        "k375wtev",
        "zyc4zrjy",
        "qkdnryqb",
        "zwb2y8km",
        "fcay6nut",
    ],
    "yjqf7v9v": ["ydpnc9mf", "ef85utgk"],
    "bdd6c9j3": ["gwehaf23"],
    "vwry4h63": ["st4p8zed"],
    "ec76xxgh": ["hh7ebbbe", "u5xdyfgz", "t8rbsm2a", "q6vcqbzu", "q5kr5te5"],
    "udu7qscz": ["fuj6anwg", "axngjxub", "dkck9r3r", "r6ca5yu4", "mxrsfxy4"],
    "q9xugjwb": [
        "yzdb8g5r",
        "ravurjmy",
        "db9ruw47",
        "d2cpckx9",
        "ku9ebnsd",
        "v5yrsupc",
        "wqzhave3",
        "sw6pmuq2",
        "pkkse7zf",
        "qjcb3yuv",
        "zedpt4vu",
        "vz5bvcxc",
        "gxf8jxvp",
    ],
    "btd2eeru": ["fswuhm29"],
    "bhbdt92x": ["nd99gn3m", "nagmfwha", "yen737zx", "bqs27azt", "ha6r2yfa"],
    "cck67jcc": ["unag2um9", "rte4m899", "e5q4tnzz", "a7gjfp23"],
    "n9earhzc": ["n4wk3hnh"],
    "cgrezw3c": ["ct992q4f"],
    "xn3rcadj": [
        "d3hca9zh",
        "qynbe79s",
        "k8y3ehvz",
        "hde9s6m6",
        "s4myqy9x",
        "z4kbdxyb",
        "ftw6fydd",
    ],
    "v4auzads": [
        "sryse3u2",
        "az6thqpf",
        "ckk8mndk",
        "tfwumz8e",
        "bhdk4yuj",
        "yz2srsuj",
        "gkggnk53",
        "gytd2sut",
        "ctz4bhzk",
    ],
    "hb3cjg65": [
        "r8c29bv5",
        "ajcf9xse",
        "v4u2fmfx",
        "qvy2etpc",
        "sk2gzqcu",
        "b6gdwrjf",
        "fmu2qcmc",
        "p9xru368",
        "kuwpu9bs",
        "z2dztk2z",
        "gut8nbwx",
    ],
    "z39jawjc": ["j5t5spxq", "gjbvwq9x", "s8y5hayx"],
}

CONCEPT_TYPES_EXPECTED = {
    "eva7r2dw": ["Concept", "Subject"],
    "abnkygk5": ["Person"],
    "q63uwwwc": ["Person"],
    "nhpckjtf": ["Agent"],
    "zcy5ht5c": ["Person"],
    "r38fesng": ["Concept"],
    "j9ch5nx6": ["Person"],
    "n4agcxsk": ["Person"],
    "s4jzf93c": ["Concept"],
    "ejkj6zcm": ["Person"],
    "d46dtqnm": ["Person"],
    "qf79cev7": ["Concept"],
    "jbdytqv9": ["Place", "Organisation"],
    "zmw4zh85": ["Person", "Agent"],
    "fgdejxqy": ["Concept", "Subject"],
    "aj998679": ["Concept", "Subject"],
    "nr24heju": ["Person", "Agent"],
    "h8wz9hwz": ["Concept", "Genre"],
    "jvpu6b3k": ["Concept", "Subject"],
    "k7cv9fmb": ["Organisation"],
}

CONCEPT_RELATED_TO_EXPECTED = {"eva7r2dw": ["zvpgcgjv", "sjxv6uys", "kx79h6jm"]}
CONCEPT_FREQUENT_COLLABORATORS_EXPECTED = {
    "eva7r2dw": [],
    "gk2eca5r": ["m78s9aek", "ar4dsxxw", "xcmmxsp2"],
}


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

        self._show_warnings(mismatches_expected, mismatches_returned)

    def _show_warnings(self, expected: dict, returned: dict):
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
        return expected_data == returned_data


class WorkAncestorsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return [a["work"]["~id"] for a in raw_returned_data["ancestors"]]


class SameAsConceptsTest(GraphQueryTest):
    def extract_single(self, raw_returned_data: dict) -> list[str]:
        return raw_returned_data["same_as_ids"]

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
        return raw_returned_data["types"]

    def compare_single(self, expected_data: list[str], returned_data: dict) -> bool:
        return sorted(expected_data) == sorted(returned_data)


def test_work_ancestors() -> None:
    WorkAncestorsTest(
        query=WORK_ANCESTORS_QUERY, expected_results=WORK_ANCESTORS_EXPECTED
    ).run()


def test_work_no_ancestors() -> None:
    response = client.run_open_cypher_query(
        WORK_ANCESTORS_QUERY, {"ids": ["c245ztkp", "a2239muq"]}
    )
    assert len(response) == 0


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
