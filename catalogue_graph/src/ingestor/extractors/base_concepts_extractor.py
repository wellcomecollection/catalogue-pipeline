from abc import ABC, abstractmethod
from collections import defaultdict
from collections.abc import Generator, Iterable
from concurrent.futures import ThreadPoolExecutor
from itertools import batched
from typing import get_args

import structlog

from clients.neptune_client import NeptuneClient
from ingestor.models.neptune.node import ImageNode, SourceConceptNode
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    ExtractedRelatedConcept,
)
from utils.types import ConceptType

from .base_extractor import (
    ConceptQuery,
    ConceptRelatedQuery,
    GraphBaseExtractor,
)

logger = structlog.get_logger(__name__)

CONCEPT_QUERY_PARAMS = {
    # There are a few Wikidata supernodes which cause performance issues in queries.
    # We need to filter them out when running queries to get related nodes.
    # Q5 -> 'human', Q151885 -> 'concept'
    "ignored_wikidata_ids": ["Q5", "Q151885"],
    # Maximum number of related nodes to return for each relationship type
    "related_to_limit": 10,
    # Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
    "shared_works_count_threshold": 3,
    "portrait_genre_ids": ["vchuk4fs", "gy4433gr", "dhgr6mj5"],
}

RelatedConcepts = dict[str, list[ExtractedRelatedConcept]]

CONCEPTS_BATCH_SIZE = 40_000


class GraphBaseConceptsExtractor(GraphBaseExtractor, ABC):
    """Abstract base class for concept extraction from the catalogue graph.

    Provides shared infrastructure used by all concept extractors: consistent batching of concept IDs, synonymous
    concept resolution, concept type lookup, and related concept merging.

    Subclasses must implement `get_concept_ids_to_process` to supply the stream of concept IDs that should be extracted.
    """

    def __init__(self, neptune_client: NeptuneClient):
        super().__init__(neptune_client)

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
        result = self.make_neptune_query("concept_type", concept_ids)

        concept_types = defaultdict(set)
        for concept_id in concept_ids:
            for same_as_id in self.get_same_as(concept_id):
                # Use the 'Concept' type by default if no other type available
                types = result.get(same_as_id, {}).get("types", ["Concept"])
                concept_types[concept_id].update(types)

        return concept_types

    def _resolve_portraits_concepts(
        self, concept_id: str, portraits_batch: dict[str, dict]
    ) -> list[ImageNode]:
        resolved_portraits = {}
        for same_as_id in self.get_same_as(concept_id):
            source = portraits_batch.get(same_as_id, {})
            for portrait in source.get("portrait_images", []):
                resolved_portraits[portrait["~id"]] = ImageNode.model_validate(portrait)

        return list(resolved_portraits.values())

    def _resolve_source_concepts(
        self, concept_id: str, source_concepts_batch: dict[str, dict]
    ) -> list[SourceConceptNode]:
        """
        If a source concept `SC` is connected to some concept `C`, `SC` is included on all concepts synonymous with `C`.
        """
        resolved_source_concepts = {}
        for same_as_id in self.get_same_as(concept_id):
            source = source_concepts_batch.get(same_as_id, {})
            for linked_sc in source.get("source_concepts", []):
                resolved_source_concepts[linked_sc["~id"]] = (
                    SourceConceptNode.model_validate(linked_sc)
                )

        return list(resolved_source_concepts.values())

    def _update_same_as_map(self, concept_ids: Iterable[str]) -> None:
        """Given a list of concept IDs, retrieve all synonymous ('same as') concepts and store them in a lookup table"""
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

    @abstractmethod
    def get_concept_ids_to_process(self) -> Generator[str]:
        pass

    def get_consistent_batches(self) -> Generator[set[str]]:
        """
        Yield consistent batches of concept IDs. Each consistent batch contains full groups of synonymous (same as)
        concepts, which must always be processed together for consistency.
        """
        processed_ids: set[str] = set()

        concept_ids_to_process = self.get_concept_ids_to_process()
        for extracted_batch in batched(concept_ids_to_process, CONCEPTS_BATCH_SIZE):
            # Some concepts might be duplicated (since a concept can appear in multiple works)
            batch = set(extracted_batch).difference(processed_ids)
            self._update_same_as_map(batch)

            # All 'same as' concepts must always be processed as part of the same batch
            # to keep the production concepts index consistent
            full_batch = set()
            for concept_id in batch:
                for same_as_id in self.get_same_as(concept_id):
                    processed_ids.add(same_as_id)
                    full_batch.add(same_as_id)

            yield full_batch

    def get_concepts(self, ids: Iterable[str]) -> dict[str, ExtractedConcept]:
        """Given a list of concept IDs, return a dictionary mapping each id to its corresponding ExtractedConcept."""
        concepts_batch = self.make_neptune_query("concept", ids)
        source_concepts_batch = self.make_neptune_query("source_concept", ids)
        concept_types_batch = self.get_concept_types(ids)
        concept_portraits_batch = self.make_neptune_query("concept_portrait", ids)

        concepts = {}
        for concept_id, concept in concepts_batch.items():
            source = source_concepts_batch.get(concept_id, {})

            # Remove `concept_id` from the list of 'same as' concepts
            same_as = set(self.get_same_as(concept_id)).difference([concept_id])

            concepts[concept_id] = ExtractedConcept(
                concept=concept["concept"],
                types=list(sorted(concept_types_batch[concept_id])),
                same_as=list(sorted(same_as)),
                linked_source_concepts=source.get("linked_source_concepts", []),
                source_concepts=self._resolve_source_concepts(
                    concept_id, source_concepts_batch
                ),
                portraits=self._resolve_portraits_concepts(
                    concept_id, concept_portraits_batch
                ),
            )

        return concepts

    def get_related_concepts(
        self, ids: Iterable[str]
    ) -> dict[ConceptRelatedQuery, RelatedConcepts]:
        """
        Given a list of concept IDs, return a nested dictionary of the following shape:
        {'some_relationship_type': {'some_concept_id': <list of ExtractedRelatedConcept>, ...}, ...}
        """
        # Run related concept queries in parallel
        with ThreadPoolExecutor() as executor:
            futures = {}
            for query in get_args(ConceptRelatedQuery):
                target = self._get_related_concepts
                futures[query] = executor.submit(target, query, ids)

            return {key: future.result() for key, future in futures.items()}
