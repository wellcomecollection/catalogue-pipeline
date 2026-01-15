"""Regenerate JSON fixtures for graph integration tests.

Usage:
    AWS_PROFILE=platform-developer uv run integration/graph/generate_fixtures.py
"""

from __future__ import annotations

import json
import random
from collections.abc import Callable
from pathlib import Path
from typing import Any

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
from ingestor.queries.work_queries import WORK_ANCESTORS_QUERY
from utils.aws import get_neptune_client

FIXTURE_SAMPLE_SIZE = 20
ID_POOL_SIZE = 10_000


def write_fixture(file_name: str, data: dict[str, Any] | list[str]) -> None:
    path = Path(__file__).parent / "fixtures" / file_name
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")


def sample_ids(*, client: Any, label: str) -> list[str]:
    query = f"MATCH (n: {label}) RETURN id(n) AS id"
    results = client.run_open_cypher_query(query)
    ids = [item["id"] for item in results]
    print(f"Retrieved {len(ids)} IDs of type {label}.")

    pool_size = min(ID_POOL_SIZE, len(ids))
    return random.sample(ids, pool_size)


def generate_fixture_set(
    *,
    client: Any,
    query: str,
    ids: list[str],
    extractor: Callable[[dict[str, Any]], list[str]],
    expected_mapping_file: str,
    empty_ids_file: str | None,
) -> None:
    response = client.run_open_cypher_query(query, {"ids": ids, **CONCEPT_QUERY_PARAMS})

    mappings: dict[str, list[str]] = {}
    for item in response:
        extracted = extractor(item)
        if extracted:
            mappings[item["id"]] = extracted

    missing_ids = {expected_id for expected_id in ids if expected_id not in mappings}

    sample_size = min(FIXTURE_SAMPLE_SIZE, len(mappings))
    sampled_ids = set(random.sample(sorted(mappings), sample_size))
    sampled_mappings = {k: v for k, v in mappings.items() if k in sampled_ids}
    write_fixture(expected_mapping_file, sampled_mappings)

    if empty_ids_file is None:
        return

    if not missing_ids:
        write_fixture(empty_ids_file, [])
        return

    random_missing = random.sample(
        sorted(missing_ids),
        min(FIXTURE_SAMPLE_SIZE, len(missing_ids)),
    )
    write_fixture(empty_ids_file, random_missing)


def types_extractor(item: dict[str, Any]) -> list[str]:
    types: list[str] = item["types"]
    return types


def related_extractor(item: dict[str, Any]) -> list[str]:
    return [r["id"] for r in item["related"]]


def same_as_extractor(item: dict[str, Any]) -> list[str]:
    same_as_ids: list[str] = item["same_as_ids"]
    return same_as_ids


def ancestors_extractor(item: dict[str, Any]) -> list[str]:
    return [a["work"]["~id"] for a in item["ancestors"]]


def main() -> None:
    client = get_neptune_client(True)

    random_concept_ids = sample_ids(client=client, label="Concept")
    random_work_ids = sample_ids(client=client, label="Work")

    generate_fixture_set(
        client=client,
        query=CONCEPT_TYPE_QUERY,
        ids=random_concept_ids,
        extractor=types_extractor,
        expected_mapping_file="concept_types_by_concept_id.json",
        empty_ids_file=None,
    )
    generate_fixture_set(
        client=client,
        query=FREQUENT_COLLABORATORS_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_frequent_collaborators_by_concept_id.json",
        empty_ids_file="concept_ids_without_frequent_collaborators.json",
    )
    generate_fixture_set(
        client=client,
        query=SAME_AS_CONCEPT_QUERY,
        ids=random_concept_ids,
        extractor=same_as_extractor,
        expected_mapping_file="concept_same_as_by_concept_id.json",
        empty_ids_file="concept_ids_without_same_as.json",
    )
    generate_fixture_set(
        client=client,
        query=RELATED_TO_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_related_to_by_concept_id.json",
        empty_ids_file="concept_ids_without_related_to.json",
    )

    generate_fixture_set(
        client=client,
        query=RELATED_TOPICS_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_related_topics_by_concept_id.json",
        empty_ids_file="concept_ids_without_related_topics.json",
    )

    generate_fixture_set(
        client=client,
        query=FIELDS_OF_WORK_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_fields_of_work_by_concept_id.json",
        empty_ids_file="concept_ids_without_fields_of_work.json",
    )

    generate_fixture_set(
        client=client,
        query=NARROWER_THAN_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_narrower_than_by_concept_id.json",
        empty_ids_file="concept_ids_without_narrower_than.json",
    )

    generate_fixture_set(
        client=client,
        query=BROADER_THAN_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_broader_than_by_concept_id.json",
        empty_ids_file="concept_ids_without_broader_than.json",
    )

    generate_fixture_set(
        client=client,
        query=PEOPLE_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_people_by_concept_id.json",
        empty_ids_file="concept_ids_without_people.json",
    )

    generate_fixture_set(
        client=client,
        query=HAS_FOUNDER_QUERY,
        ids=random_concept_ids,
        extractor=related_extractor,
        expected_mapping_file="concept_has_founder_by_concept_id.json",
        empty_ids_file="concept_ids_without_has_founder.json",
    )
    generate_fixture_set(
        client=client,
        query=WORK_ANCESTORS_QUERY,
        ids=random_work_ids,
        extractor=ancestors_extractor,
        expected_mapping_file="work_ancestors_by_work_id.json",
        empty_ids_file="work_ids_without_ancestors.json",
    )


if __name__ == "__main__":
    main()
