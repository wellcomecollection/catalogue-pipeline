from collections import defaultdict
from collections.abc import Generator, Iterable
from concurrent.futures import ThreadPoolExecutor
from itertools import batched
from typing import get_args

from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    ExtractedRelatedConcept,
)
from models.events import IncrementalWindow
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode
from utils.types import ConceptType

from .base_extractor import (
    ConceptQuery,
    ConceptRelatedQuery,
    GraphBaseExtractor,
)

CONCEPT_QUERY_PARAMS = {
    # There are a few Wikidata supernodes which cause performance issues in queries.
    # We need to filter them out when running queries to get related nodes.
    # Q5 -> 'human', Q151885 -> 'concept'
    "ignored_wikidata_ids": ["Q5", "Q151885"],
    # Maximum number of related nodes to return for each relationship type
    "related_to_limit": 10,
    # Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
    "shared_works_count_threshold": 3,
}

ES_QUERY = {"match": {"type": "Visible"}}
# Only retrieve the fields we need (concept IDs and types)
ES_FIELDS = [
    "data.subjects.id.canonicalId",
    "data.subjects.concepts.id.canonicalId",
    "data.subjects.concepts.type",
    "data.subjects.type",
    "data.contributors.agent.id.canonicalId",
    "data.genres.concepts.id.canonicalId",
    "data.genres.concepts.type",
]

RelatedConcepts = dict[str, list[ExtractedRelatedConcept]]

CONCEPTS_BATCH_SIZE = 40_000


class GraphConceptsExtractor(GraphBaseExtractor):
    def __init__(
        self,
        pipeline_date: str,
        window: IncrementalWindow | None,
        es_mode: ElasticsearchMode,
    ):
        super().__init__(es_mode != "private")
        self.es_source = MergedWorksSource(
            pipeline_date=pipeline_date,
            query=ES_QUERY,
            fields=ES_FIELDS,
            window=window,
            es_mode=es_mode,
        )

        self.primary_map: dict[str, str] = {}
        self.same_as_map: dict[str, list[str]] = {}

        self.neptune_params = CONCEPT_QUERY_PARAMS

    def get_primary(self, concept_id: str) -> str:
        """
        Given a `concept_id`, return its primary 'same as' concept ID. Each group of 'same as' concepts has exactly
        one primary ID.
        """
        return self.primary_map.get(concept_id, concept_id)

    def get_same_as(self, concept_id: str) -> list[str]:
        """Given a `concept_id`, return a list of all 'same as' concept IDs (including `concept_id` itself)."""
        primary_id = self.get_primary(concept_id)
        return self.same_as_map.get(primary_id, [primary_id])

    def get_concept_types(
        self, concept_ids: Iterable[str]
    ) -> dict[str, set[ConceptType]]:
        """Given a set of `concept_ids`, return all types associated with each concept via HAS_CONCEPT edges."""
        # Types are shared across all 'same as' concepts
        concept_ids = set().union(*(self.get_same_as(i) for i in concept_ids))

        result = self.make_neptune_query("concept_type", concept_ids)

        concept_types = defaultdict(set)
        for concept_id in concept_ids:
            for same_as_id in self.get_same_as(concept_id):
                # Use the 'Concept' type by default if no other type available
                types = result.get(same_as_id, {}).get("types", ["Concept"])
                concept_types[concept_id].update(types)

        return concept_types

    def get_concepts(self, ids: Iterable[str]) -> dict[str, ExtractedConcept]:
        concept_result = self.make_neptune_query("concept", ids)
        source_concept_result = self.make_neptune_query("source_concept", ids)
        concept_types = self.get_concept_types(ids)

        concepts = {}
        for concept_id, concept in concept_result.items():
            source = source_concept_result.get(concept_id, {})

            # Remove `concept_id` from the list of 'same as' concepts
            same_as = set(self.get_same_as(concept_id)).difference([concept_id])

            # Each concept should have at most one linked source concept
            if len(source.get("linked_source_concepts", [])) == 0:
                linked_source_concept = None
            else:
                linked_source_concept = source["linked_source_concepts"][0]

            concepts[concept_id] = ExtractedConcept(
                concept=concept["concept"],
                types=list(concept_types[concept_id]),
                same_as=list(same_as),
                linked_source_concept=linked_source_concept,
                source_concepts=source.get("source_concepts", []),
            )

        return concepts

    def _update_same_as_map(self, concept_ids: Iterable[str]) -> None:
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

    def _get_related_concepts(
        self, query_type: ConceptQuery, ids: Iterable[str]
    ) -> RelatedConcepts:
        result = self.make_neptune_query(query_type, ids)

        related_ids = set()
        for item in result.values():
            related_ids.update([i["id"] for i in item["related"]])

        self._update_same_as_map(related_ids)

        # Merge results across 'same as' concepts and related concepts
        merged_result: dict[str, dict] = defaultdict(dict)
        for concept_id, item in result.items():
            primary_id = self.get_primary(concept_id)

            for related in item["related"]:
                primary_related_id = self.get_primary(related["id"])

                # Skip if a concept is related to itself
                if self.get_primary(primary_id) != self.get_primary(primary_related_id):
                    entry = merged_result[primary_id].setdefault(
                        primary_related_id,
                        {
                            "id": primary_related_id,
                            "count": 0,
                            "relationship_type": set(),
                        },
                    )
                    entry["count"] += related["count"]
                    if related.get("relationship_type"):
                        entry["relationship_type"].add(related["relationship_type"])

        limit = self.neptune_params["related_to_limit"]
        sorted_result = {
            concept_id: sorted(entries.values(), key=lambda i: -i["count"])[:limit]
            for concept_id, entries in merged_result.items()
        }

        primary_related_ids = {self.get_primary(i) for i in related_ids}
        full_related_concepts = self.get_concepts(primary_related_ids)

        full_result = defaultdict(list)
        for concept_id in ids:
            for entry in sorted_result.get(concept_id, []):
                related_concept = ExtractedRelatedConcept(
                    target=full_related_concepts[entry["id"]],
                    # Pick one relationship type if present, else None
                    relationship_type=next(iter(entry["relationship_type"]), None),
                )

                full_result[concept_id].append(related_concept)

        return full_result

    def get_concepts_from_works(self) -> Generator[str]:
        for work in self.es_source.stream_raw():
            # Since we only ask for concept fields, works with no concepts are returned as empty dictionaries
            if "data" in work:
                for concept, _ in extract_concepts_from_work(work["data"]):
                    if "id" not in concept:
                        print(f"Concept {concept} does not have an ID.")
                        continue

                    yield concept["id"]["canonicalId"]

    def get_concept_stream(self) -> Generator[set[str]]:
        processed_ids: set[str] = set()

        extracted_ids = self.get_concepts_from_works()
        for extracted_batch in batched(
            extracted_ids, CONCEPTS_BATCH_SIZE, strict=False
        ):
            batch = set(extracted_batch).difference(processed_ids)
            self._update_same_as_map(batch)

            # All 'same as' concepts must be processed as part of the same batch to get consistent results
            full_batch = set()
            for concept_id in batch:
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
                for query in get_args(ConceptRelatedQuery):
                    target = self._get_related_concepts
                    futures[query] = executor.submit(target, query, concept_ids)

                all_related_concepts: dict[ConceptRelatedQuery, dict] = {
                    key: future.result() for key, future in futures.items()
                }

            for concept_id, concept in concepts:
                primary_id = self.get_primary(concept_id)
                related_concepts = {}
                for key in all_related_concepts:
                    if primary_id in all_related_concepts[key]:
                        related_concepts[key] = all_related_concepts[key][primary_id]

                yield concept, related_concepts
