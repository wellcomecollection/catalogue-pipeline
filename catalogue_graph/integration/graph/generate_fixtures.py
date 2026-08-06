"""Regenerate JSON fixtures for graph integration tests.

Usage:
    # Regenerate all fixtures
    AWS_PROFILE=platform-developer uv run integration/graph/generate_fixtures.py

    # Regenerate only specific fixtures (matches test_graph_queries.py MATCH_CASES names)
    AWS_PROFILE=platform-developer uv run integration/graph/generate_fixtures.py --fixtures concept_people concept_related_to
"""

from __future__ import annotations

import argparse
import json
import random
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from getpass import getuser
from pathlib import Path
from typing import Any, Literal

from clients.neptune_client import NeptuneClient
from ingestor.extractors.concepts.base_concepts_extractor import CONCEPT_QUERY_PARAMS
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


@dataclass
class FixtureSpec:
    name: str
    query: str
    id_label: Literal["Concept", "Work"]
    row_to_values: Callable[[dict[str, Any]], list[str]]
    expected_fixture_name: str
    empty_ids_fixture_name: str | None


FIXTURE_SPECS: list[FixtureSpec] = [
    FixtureSpec(
        name="concept_types",
        query=CONCEPT_TYPE_QUERY,
        id_label="Concept",
        row_to_values=row_to_types,
        expected_fixture_name="concept_types_by_concept_id",
        empty_ids_fixture_name=None,
    ),
    FixtureSpec(
        name="concept_frequent_collaborators",
        query=FREQUENT_COLLABORATORS_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_frequent_collaborators_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_frequent_collaborators",
    ),
    FixtureSpec(
        name="concept_same_as",
        query=SAME_AS_CONCEPT_QUERY,
        id_label="Concept",
        row_to_values=row_to_same_as_ids,
        expected_fixture_name="concept_same_as_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_same_as",
    ),
    FixtureSpec(
        name="concept_related_to",
        query=RELATED_TO_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_related_to_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_related_to",
    ),
    FixtureSpec(
        name="concept_related_topics",
        query=RELATED_TOPICS_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_related_topics_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_related_topics",
    ),
    FixtureSpec(
        name="concept_fields_of_work",
        query=FIELDS_OF_WORK_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_fields_of_work_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_fields_of_work",
    ),
    FixtureSpec(
        name="concept_narrower_than",
        query=NARROWER_THAN_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_narrower_than_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_narrower_than",
    ),
    FixtureSpec(
        name="concept_broader_than",
        query=BROADER_THAN_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_broader_than_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_broader_than",
    ),
    FixtureSpec(
        name="concept_people",
        query=PEOPLE_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_people_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_people",
    ),
    FixtureSpec(
        name="concept_has_founder",
        query=HAS_FOUNDER_QUERY,
        id_label="Concept",
        row_to_values=row_to_related_ids,
        expected_fixture_name="concept_has_founder_by_concept_id",
        empty_ids_fixture_name="concept_ids_without_has_founder",
    ),
    FixtureSpec(
        name="work_ancestors",
        query=WORK_ANCESTORS_QUERY,
        id_label="Work",
        row_to_values=row_to_ancestor_work_ids,
        expected_fixture_name="work_ancestors_by_work_id",
        empty_ids_fixture_name="work_ids_without_ancestors",
    ),
]

FIXTURE_NAMES = [spec.name for spec in FIXTURE_SPECS]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fixtures",
        nargs="+",
        choices=FIXTURE_NAMES,
        default=None,
        help="Only regenerate these fixtures (default: regenerate all).",
    )
    return parser.parse_args()


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
    args = parse_args()
    selected_names = args.fixtures or FIXTURE_NAMES
    specs = [spec for spec in FIXTURE_SPECS if spec.name in selected_names]

    reason = confirm_regeneration()
    append_regeneration_log(
        reason=f"{reason} (fixtures: {', '.join(spec.name for spec in specs)})"
    )

    graph_date = input("Enter the graph date (e.g. 2025-01-01): ").strip()
    client = NeptuneClient(graph_date)

    # Only fetch the ID pools actually needed by the selected fixtures.
    id_labels = {spec.id_label for spec in specs}
    id_pools = {label: sample_ids(client=client, label=label) for label in id_labels}

    for spec in specs:
        generate_fixture_set(
            client=client,
            query=spec.query,
            ids=id_pools[spec.id_label],
            row_to_values=spec.row_to_values,
            expected_fixture_name=spec.expected_fixture_name,
            empty_ids_fixture_name=spec.empty_ids_fixture_name,
        )


if __name__ == "__main__":
    main()
