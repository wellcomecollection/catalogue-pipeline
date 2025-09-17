import time
from collections import defaultdict
from collections.abc import Generator, Iterable
from concurrent.futures import ThreadPoolExecutor
from itertools import batched
from typing import Literal, get_args

from ingestor.models.neptune.query_result import NeptuneConcept
from ingestor.queries.concept_queries import (
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
    get_referenced_together_query,
    get_related_query,
)
from models.events import IncrementalWindow
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.merged_works_source import MergedWorksSource
from utils.aws import get_neptune_client
from utils.streaming import process_stream_in_parallel

NEPTUNE_PARAMS = {
    # There are a few Wikidata supernodes which cause performance issues in queries.
    # We need to filter them out when running queries to get related nodes.
    # Q5 -> 'human', Q151885 -> 'concept'
    "ignored_wikidata_ids": ["Q5", "Q151885"],
    # Maximum number of related nodes to return for each relationship type
    "related_to_limit": 10,
    # Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
    "shared_works_count_threshold": 3,
}

RelatedQuery = Literal[
    "related_to", "fields_of_work", "narrower_than", "broader_than", "people"
]
ReferencedTogetherQuery = Literal["frequent_collaborators", "related_topics"]
ConceptQuery = Literal[
    "concept",
    "concept_type",
    "source_concept",
    "same_as_concept",
    RelatedQuery,
    ReferencedTogetherQuery,
]

EXPENSIVE_QUERIES = {"related_topics", "frequent_collaborators"}

NEPTUNE_QUERIES = {
    "concept": CONCEPT_QUERY,
    "concept_type": CONCEPT_TYPE_QUERY,
    "source_concept": SOURCE_CONCEPT_QUERY,
    "same_as_concept": SAME_AS_CONCEPT_QUERY,
    "related_to": get_related_query("RELATED_TO"),
    "fields_of_work": get_related_query("HAS_FIELD_OF_WORK"),
    "narrower_than": get_related_query("NARROWER_THAN"),
    "broader_than": get_related_query("NARROWER_THAN|HAS_PARENT", "to"),
    "people": get_related_query("HAS_FIELD_OF_WORK", "to"),
    # Retrieve people and orgs which are commonly referenced together as collaborators with a given person/org
    "frequent_collaborators": get_referenced_together_query(
        source_referenced_types=["Person", "Organisation"],
        related_referenced_types=["Person", "Organisation"],
        source_referenced_in=["contributors"],
        related_referenced_in=["contributors"],
    ),
    # Do not include agents/people/orgs in the list of related topics.
    "related_topics": get_referenced_together_query(
        related_referenced_types=[
            "Concept",
            "Subject",
            "Place",
            "Meeting",
            "Period",
            "Genre",
        ],
        related_referenced_in=["subjects"],
    ),
}

ES_QUERY = {"match": {"type": "Visible"}}
ES_FIELDS = [
    "data.subjects",
    "data.contributors",
    "data.genres",
]

LinkedConcepts = dict[str, list[dict]]


class GraphConceptsExtractor:
    def __init__(
        self,
        pipeline_date: str,
        window: IncrementalWindow = None,
        is_local: bool = False,
    ):
        self.es_source = MergedWorksSource(
            pipeline_date=pipeline_date,
            query=ES_QUERY,
            fields=ES_FIELDS,
            window=window,
            is_local=is_local,
        )
        self.neptune_client = get_neptune_client(is_local)

        self.primary_map = {}
        self.same_as_map = {}

    def get_primary(self, concept_id: str):
        """
        Given a `concept_id`, return its primary 'same as' concept ID. Each group of 'same as' concepts has exactly
        one primary ID.
        """
        return self.primary_map.get(concept_id, concept_id)

    def get_same_as(self, concept_id: str) -> list[str]:
        """Given a `concept_id`, return a list of all 'same as' concept IDs (including `concept_id` itself)."""
        primary_id = self.get_primary(concept_id)
        return self.same_as_map.get(primary_id, [primary_id])

    def make_neptune_query(self, query_type: ConceptQuery, ids: Iterable[str]) -> dict:
        chunk_size = 1000 if query_type in EXPENSIVE_QUERIES else 5000

        def _run_query(chunk: Iterable[str]):
            return self.neptune_client.run_open_cypher_query(
                NEPTUNE_QUERIES[query_type], NEPTUNE_PARAMS | {"ids": chunk}
            )

        start = time.time()
        raw_result = process_stream_in_parallel(ids, _run_query, chunk_size, 5)
        results = {item["id"]: item for item in raw_result}

        print(
            f"Ran a set of '{query_type}' queries in {round(time.time() - start)}s, "
            f"retrieving {len(results)} records."
        )
        return results

    def get_concept_types(self, concept_ids: Iterable[str]):
        """Given a set of `concept_ids`, return all types associated with each concept via HAS_CONCEPT edges."""

        # Types are shared across all 'same as' concepts
        concept_ids = set().union(*(self.get_same_as(i) for i in concept_ids))

        result = self.make_neptune_query("concept_type", concept_ids)

        concept_types = defaultdict(set)
        for concept_id in concept_ids:
            for same_as_id in self.get_same_as(concept_id):
                # Use the 'Concept' type by default if no other type available
                types = result.get(same_as_id, ["Concept"])
                concept_types[concept_id].update(types)

        return concept_types

    def get_concepts(self, ids: Iterable[str]):
        concept_result = self.make_neptune_query("concept", ids)
        source_concept_result = self.make_neptune_query("source_concept", ids)
        concept_types = self.get_concept_types(ids)

        concepts = {}
        for concept_id, concept in concept_result.items():
            source = source_concept_result.get(concept_id, {})

            concepts[concept_id] = NeptuneConcept(
                concept=concept["concept"],
                types=concept_types["concept_id"],
                same_as=self.get_same_as(concept_id),
                linked_source_concept=source.get("linked_source_concepts", [None])[0],
                source_concepts=source.get("source_concepts", []),
            )

        return concepts

    def _update_same_as_map(self, concept_ids: Iterable[str]):
        # Remove concepts whose 'same as' IDs were already calculated as part of a previous batch
        concept_ids = set(concept_ids).difference(self.primary_map.keys())

        result = self.make_neptune_query("same_as_concept", concept_ids)

        for concept_id, item in result.items():
            same_as_ids = [concept_id] + item["same_as_ids"]

            # Alphabetical ID-based prioritisation
            primary_id = sorted(same_as_ids)[0]

            self.same_as_map[primary_id] = same_as_ids
            for same_as_id in same_as_ids:
                self.primary_map[same_as_id] = primary_id

        return result

    def _get_related_concepts(self, query_type: ConceptQuery, ids: Iterable[str]):
        result = self.make_neptune_query(query_type, ids)

        related_ids = set()
        for item in result.values():
            related_ids.update([i["id"] for i in item["related"]])

        self._update_same_as_map(related_ids)

        # Merge results across 'same as' concepts and related concepts
        merged_result = defaultdict(lambda: defaultdict(lambda: 0))
        for concept_id, item in result.items():
            primary_id = self.get_primary(concept_id)

            for related in item["related"]:
                primary_related_id = self.get_primary(related["id"])

                # Make sure a concept is not related to itself
                if self.get_primary(primary_id) != self.get_primary(primary_related_id):
                    merged_result[primary_id][primary_related_id] += related["count"]

        sorted_result = {}
        for concept_id, related in merged_result.items():
            sorted_items = sorted(related.items(), key=lambda i: -i[1])
            limit = NEPTUNE_PARAMS["related_to_limit"]
            sorted_result[concept_id] = [i[0] for i in sorted_items[:limit]]

        primary_related_ids = {self.get_primary(i) for i in related_ids}
        full_related_concepts = self.get_concepts(primary_related_ids)

        full_result = defaultdict(list)

        for concept_id in ids:
            for related_id in sorted_result.get(concept_id, []):
                related_concept = full_related_concepts[related_id]
                full_result[concept_id].append(related_concept)

        return full_result

    def get_concepts_from_works(self) -> Generator[str]:
        for work in self.es_source.stream_raw():
            for concept, _ in extract_concepts_from_work(work["data"]):
                concept_id = concept["id"].get("canonicalId")

                if concept_id:
                    yield concept_id
                else:
                    print(f"Concept {concept} does not have an ID.")

    def get_concept_stream(self) -> Generator[set[str]]:
        processed_ids = set()

        extracted_ids = self.get_concepts_from_works()
        for extracted_batch in batched(extracted_ids, 10_000, strict=False):
            extracted_batch = set(extracted_batch).difference(processed_ids)
            self._update_same_as_map(extracted_batch)

            # All 'same as' concepts must be processed as part of the same batch to get consistent results
            full_batch = set()
            for concept_id in extracted_batch:
                for same_as_id in self.get_same_as(concept_id):
                    processed_ids.add(same_as_id)
                    full_batch.add(same_as_id)

            yield full_batch

    def extract_raw(self) -> Generator[tuple]:
        for concept_ids in self.get_concept_stream():
            print(f"Will process a batch of {len(concept_ids)} concepts.")
            concepts = self.get_concepts(concept_ids).items()

            # Run related concept queries in parallel
            with ThreadPoolExecutor() as executor:
                futures = {}
                for query in get_args(RelatedQuery) + get_args(ReferencedTogetherQuery):
                    target = self._get_related_concepts
                    futures[query] = executor.submit(target, query, concept_ids)

                all_related_concepts = {
                    key: future.result() for key, future in futures.items()
                }

            for concept_id, concept in concepts:
                primary_id = self.get_primary(concept_id)
                related_concepts = {}
                for key in all_related_concepts:
                    if primary_id in all_related_concepts[key]:
                        related_concepts[key] = all_related_concepts[key][primary_id]

                yield concept, related_concepts
