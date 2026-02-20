"""Regenerate JSON fixtures for graph integration tests.

Usage:
    AWS_PROFILE=platform-developer uv run integration/graph/generate_fixtures.py
"""

from __future__ import annotations

import json
import random
from collections.abc import Callable
from datetime import UTC, datetime
from getpass import getuser
from pathlib import Path
from typing import Any

from clients.neptune_client import NeptuneClient
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

FIXTURE_SAMPLE_SIZE = 20
ID_POOL_SIZE = 20_000
REGENERATION_LOG_NAME = "REGENERATION_LOG.md"


def write_fixture(name: str, data: dict[str, Any] | list[str]) -> None:
    path = Path(__file__).parent / "fixtures" / f"{name}.json"
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
    print(f"Wrote fixture: {path}")


def append_regeneration_log(*, reason: str) -> None:
    fixtures_dir = Path(__file__).parent / "fixtures"
    path = fixtures_dir / REGENERATION_LOG_NAME

    timestamp = datetime.now(UTC).isoformat(timespec="seconds")
    username = getuser()

    fixtures_dir.mkdir(parents=True, exist_ok=True)
    is_new_or_empty = (not path.exists()) or path.stat().st_size == 0

    cleaned_reason = " ".join(reason.splitlines()).strip()

    with path.open("a", encoding="utf-8") as f:
        if is_new_or_empty:
            f.write("# Fixture regeneration log\n\n")
        f.write(f"- {timestamp} ({username}): {cleaned_reason}\n")


def sample_ids(*, client: Any, label: str) -> list[str]:
    query = f"MATCH (n: {label}) RETURN id(n) AS id"
    results = client.run_open_cypher_query(query)
    ids = [item["id"] for item in results]

    print(f"Retrieved {len(ids)} IDs of type {label}.")
    return random.sample(ids, ID_POOL_SIZE)


def generate_fixture_set(
    *,
    client: Any,
    query: str,
    ids: list[str],
    row_to_values: Callable[[dict[str, Any]], list[str]],
    expected_fixture_name: str,
    empty_ids_fixture_name: str | None,
) -> None:
    """Generate two fixtures for each query:

    - 'expected fixture': a mapping of id -> extracted value (e.g. related concept IDs)
    - 'empty IDs fixture': a list of IDs for which the query should be empty
    """
    response = client.run_open_cypher_query(query, {"ids": ids, **CONCEPT_QUERY_PARAMS})

    mappings: dict[str, list[str]] = {}
    for item in response:
        extracted = row_to_values(item)
        if extracted:
            mappings[item["id"]] = extracted

    missing_ids = {expected_id for expected_id in ids if expected_id not in mappings}

    sampled_ids = set(random.sample(sorted(mappings), FIXTURE_SAMPLE_SIZE))
    sampled_mappings = {k: v for k, v in mappings.items() if k in sampled_ids}
    write_fixture(expected_fixture_name, sampled_mappings)

    if empty_ids_fixture_name is None:
        return

    random_missing = random.sample(sorted(missing_ids), FIXTURE_SAMPLE_SIZE)
    write_fixture(empty_ids_fixture_name, random_missing)


def row_to_types(item: dict[str, Any]) -> list[str]:
    types: list[str] = item["types"]
    return types


def row_to_related_ids(item: dict[str, Any]) -> list[str]:
    return [r["id"] for r in item["related"]]


def row_to_same_as_ids(item: dict[str, Any]) -> list[str]:
    same_as_ids: list[str] = item["same_as_ids"]
    return same_as_ids


def row_to_ancestor_work_ids(item: dict[str, Any]) -> list[str]:
    return [a["work"]["~id"] for a in item["ancestors"]]


def confirm_regeneration() -> str:
    print(
        "\n".join(
            [
                "WARNING: This script regenerates integration test fixtures from live production Cypher queries.",
                "If a production query is wrong, regenerating fixtures may hide the bug rather than fix it.",
                "Only continue if you have investigated why tests failed.",
            ]
        )
    )

    answer = input("\nContinue and overwrite fixtures? [y/N]: ").strip().lower()
    if answer not in {"y", "yes"}:
        raise SystemExit("Aborted.")

    while True:
        reason = input("Briefly describe why you're regenerating fixtures: ").strip()
        if reason:
            return reason


def main() -> None:
    reason = confirm_regeneration()
    append_regeneration_log(reason=reason)

    client = NeptuneClient()

    # Get a sample of `ID_POOL_SIZE` random concept and work IDs.
    random_concept_ids = sample_ids(client=client, label="Concept")
    random_work_ids = sample_ids(client=client, label="Work")

    generate_fixture_set(
        client=client,
        query=CONCEPT_TYPE_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_types,
        expected_fixture_name="concept_types_by_concept_id",
        empty_ids_fixture_name=None,
    )
    generate_fixture_set(
        client=client,
        query=FREQUENT_COLLABORATORS_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_frequent_collaborators_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_frequent_collaborators",
    )
    generate_fixture_set(
        client=client,
        query=SAME_AS_CONCEPT_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_same_as_ids,
        expected_fixture_name="concept_same_as_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_same_as",
    )
    generate_fixture_set(
        client=client,
        query=RELATED_TO_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_related_to_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_related_to",
    )
    generate_fixture_set(
        client=client,
        query=RELATED_TOPICS_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_related_topics_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_related_topics",
    )
    generate_fixture_set(
        client=client,
        query=FIELDS_OF_WORK_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_fields_of_work_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_fields_of_work",
    )
    generate_fixture_set(
        client=client,
        query=NARROWER_THAN_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_narrower_than_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_narrower_than",
    )
    generate_fixture_set(
        client=client,
        query=BROADER_THAN_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_broader_than_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_broader_than",
    )
    generate_fixture_set(
        client=client,
        query=PEOPLE_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_people_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_people",
    )
    generate_fixture_set(
        client=client,
        query=HAS_FOUNDER_QUERY,
        ids=random_concept_ids,
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_has_founder_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_has_founder",
    )
    generate_fixture_set(
        client=client,
        query=WORK_ANCESTORS_QUERY,
        ids=random_work_ids,
        row_to_values=row_to_ancestor_work_ids,
        expected_fixture_name="work_ancestors_by_work_id",
        empty_ids_fixture_name="work_ids_without_ancestors",
    )


if __name__ == "__main__":
    main()
