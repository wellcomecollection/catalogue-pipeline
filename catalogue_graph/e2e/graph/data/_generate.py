import json
import os
import random
from collections.abc import Callable

from ingestor.extractors.concepts_extractor import CONCEPT_QUERY_PARAMS
from ingestor.queries.concept_queries import (
    CONCEPT_TYPE_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    RELATED_TO_QUERY,
    SAME_AS_CONCEPT_QUERY,
)
from ingestor.queries.work_queries import WORK_ANCESTORS_QUERY
from utils.aws import get_neptune_client

NEPTUNE_CLIENT = get_neptune_client(True)
TEST_SAMPLE_SIZE = 20


def save_test_data(file_name: str, data: dict | list) -> None:
    path = f"{os.path.dirname(__file__)}/{file_name}"
    with open(path, "w") as f:
        f.write(json.dumps(data, indent=2))


def get_random_ids(label: str) -> list[str]:
    query = f"MATCH (n: {label}) RETURN id(n) AS id"
    r = NEPTUNE_CLIENT.run_open_cypher_query(query)
    print(f"Retrieved {len(r)} IDs of type {label}.")
    return random.sample([item["id"] for item in r], 10000)


def generate_test_data(
    query: str, ids: list[str], extractor: Callable, label: str
) -> None:
    response = NEPTUNE_CLIENT.run_open_cypher_query(
        query, {"ids": ids, **CONCEPT_QUERY_PARAMS}
    )

    mappings = {}
    for item in response:
        extracted = extractor(item)
        if extracted:
            mappings[item["id"]] = extracted

    missing_ids = set()
    for expected_id in ids:
        if expected_id not in mappings:
            missing_ids.add(expected_id)

    random_ids = set(random.sample(sorted(mappings), TEST_SAMPLE_SIZE))
    random_mappings = {k: v for k, v in mappings.items() if k in random_ids}
    save_test_data(f"{label}.json", random_mappings)

    if len(missing_ids) >= TEST_SAMPLE_SIZE:
        random_missing = random.sample(list(missing_ids), TEST_SAMPLE_SIZE)
        save_test_data(f"{label}_none.json", random_missing)
    else:
        print(f"Not enough missing data for '{label}'.")


RANDOM_CONCEPT_IDS = get_random_ids("Concept")
RANDOM_WORK_IDS = get_random_ids("Work")


def types_extractor(item: dict) -> list[str]:
    return item["types"]


def related_extractor(item: dict) -> list[str]:
    return [r["id"] for r in item["related"]]


def same_as_extractor(item: dict) -> list[str]:
    return item["same_as_ids"]


def ancestors_extractor(item: dict) -> list[str]:
    return [a["work"]["~id"] for a in item["ancestors"]]


generate_test_data(
    query=CONCEPT_TYPE_QUERY,
    ids=RANDOM_CONCEPT_IDS,
    extractor=types_extractor,
    label="concepts_types",
)

generate_test_data(
    query=FREQUENT_COLLABORATORS_QUERY,
    ids=RANDOM_CONCEPT_IDS,
    extractor=related_extractor,
    label="concepts_frequent_collaborators",
)

generate_test_data(
    query=SAME_AS_CONCEPT_QUERY,
    ids=RANDOM_CONCEPT_IDS,
    extractor=same_as_extractor,
    label="concepts_same_as",
)

generate_test_data(
    query=RELATED_TO_QUERY,
    ids=RANDOM_CONCEPT_IDS,
    extractor=related_extractor,
    label="concepts_related_to",
)

generate_test_data(
    query=WORK_ANCESTORS_QUERY,
    ids=RANDOM_WORK_IDS,
    extractor=ancestors_extractor,
    label="works_ancestors",
)
