from collections.abc import Generator, Iterator
from itertools import batched

import structlog
from pydantic import BaseModel

from clients.neptune_client import NeptuneClient
from graph.sources.catalogue.concepts_source import extract_identified_concepts
from ingestor.extractors.base_extractor import (
    GraphBaseExtractor,
)
from ingestor.extractors.concepts.work_concepts_extractor import WorkConceptsExtractor
from ingestor.models.merged.work import (
    MergedWork,
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    WorkHierarchy,
)
from models.pipeline.work_data import WorkData

logger = structlog.get_logger(__name__)

WORKS_BATCH_SIZE = 40_000


def extract_identified_concept_ids(work_data: WorkData) -> list[str]:
    work_concepts = extract_identified_concepts(work_data)
    return [c.id.canonical_id for c, _ in work_concepts]


def get_works_by_id_query(work_ids: list[str]) -> dict:
    """Return an ES query retrieving all visible works with the given IDs"""
    return {
        "bool": {
            "must": [
                {"ids": {"values": list(work_ids)}},
                {"match": {"type": "Visible"}},  # Only include visible works
            ]
        }
    }


class ExtractedWork(BaseModel):
    work: MergedWork


class VisibleExtractedWork(ExtractedWork):
    work: VisibleMergedWork
    hierarchy: WorkHierarchy
    concepts: list[ExtractedConcept]


class GraphBaseWorksExtractor(GraphBaseExtractor):
    def __init__(self, neptune_client: NeptuneClient):
        super().__init__(neptune_client)

        self.streamed_ids: set[str] = set()
        self.extracted_concepts: dict[str, ExtractedConcept] = {}

    def _extract_concepts(self, concept_ids: set[str]) -> None:
        # Cache extracted concepts to ensure each concept is only processed once,
        # even if it appears in multiple work batches
        concept_ids_to_extract = concept_ids.difference(
            set(self.extracted_concepts.keys())
        )
        concepts_extractor = WorkConceptsExtractor(
            self.neptune_client, concept_ids_to_extract
        )
        for concept_id, concept in concepts_extractor.extract_raw():
            self.extracted_concepts[concept_id] = concept

    def _get_work_ancestors(self, ids: list[str]) -> dict:
        """Return all ancestors of each work in the current batch."""
        return self.make_neptune_query("work_ancestors", ids)

    def _get_work_children(self, ids: list[str]) -> dict:
        """Return all children of each work in the current batch."""
        return self.make_neptune_query("work_children", ids)

    def _get_work_descendants(self, ids: list[str]) -> dict:
        """Return all descendants of each work in the current batch."""
        return self.make_neptune_query("work_descendants", ids)

    def get_work_concepts(self, works: dict[str, WorkData]) -> dict:
        """Return all concepts of each work in the current batch."""
        concept_ids_by_work = {}
        all_concept_ids = set()
        for work_id, work_data in works.items():
            work_concept_ids = extract_identified_concept_ids(work_data)
            concept_ids_by_work[work_id] = work_concept_ids
            all_concept_ids |= set(work_concept_ids)

        self._extract_concepts(all_concept_ids)

        concepts_by_work: dict[str, list[ExtractedConcept]] = {}
        for work_id in works:
            concepts_by_work[work_id] = []
            for concept_id in concept_ids_by_work[work_id]:
                if concept_id in self.extracted_concepts:
                    concepts_by_work[work_id].append(
                        self.extracted_concepts[concept_id]
                    )
                else:
                    logger.warning(
                        "Concept ID does not exist in the graph", concept_id=concept_id
                    )

        return concepts_by_work

    def _process_visible_batch(
        self, visible_works: list[VisibleMergedWork]
    ) -> Generator[VisibleExtractedWork]:
        visible_work_ids = [w.state.canonical_id for w in visible_works]
        work_data = {w.state.canonical_id: w.data for w in visible_works}

        # Make graph queries to retrieve ancestors, children, and concepts for all visible works in each batch
        ancestors_batch = self._get_work_ancestors(visible_work_ids)
        children_batch = self._get_work_children(visible_work_ids)
        concepts_batch = self.get_work_concepts(work_data)

        for es_work in visible_works:
            work_id = es_work.state.canonical_id
            self.streamed_ids.add(work_id)

            hierarchy = WorkHierarchy(
                id=work_id,
                ancestors=ancestors_batch.get(work_id, {}).get("ancestors", []),
                children=children_batch.get(work_id, {}).get("children", []),
            )

            yield VisibleExtractedWork(
                work=es_work, hierarchy=hierarchy, concepts=concepts_batch[work_id]
            )

    def process_es_works(
        self, es_works: Iterator[MergedWork]
    ) -> Generator[ExtractedWork]:
        for es_batch in batched(es_works, WORKS_BATCH_SIZE):
            # Yield all non-visible works first. They do not include hierarchies or concepts.
            yield from (
                ExtractedWork(work=w)
                for w in es_batch
                if not isinstance(w, VisibleMergedWork)
            )

            visible_works = [w for w in es_batch if isinstance(w, VisibleMergedWork)]
            yield from self._process_visible_batch(visible_works)
