from collections.abc import Generator, Iterator
from itertools import batched

import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from clients.neptune_client import NeptuneClient
from ingestor.models.merged.work import (
    MergedWork,
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import ExtractedConcept, WorkHierarchy
from models.events import BasePipelineEvent
from sources.merged_works_source import MergedWorksSource

from .base_extractor import GraphBaseExtractor

logger = structlog.get_logger(__name__)

WORKS_BATCH_SIZE = 40_000


def get_related_works_query(related_ids: list[str]) -> dict:
    """Return an ES query retrieving all visible works with the given IDs"""
    return {
        "bool": {
            "must": [
                {"ids": {"values": list(related_ids)}},
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


class GraphWorksExtractor(GraphBaseExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(neptune_client)
        self.es_source = MergedWorksSource(
            event,
            es_client=es_client,
        )
        self.event = event
        self.es_client = es_client

        self.streamed_ids: set[str] = set()
        self.related_ids: set[str] = set()

    def get_related_works_source(self, related_ids: list[str]) -> MergedWorksSource:
        # Remove `window` from event before retrieving related works. (All related works should be processed
        # even if they aren't part of the current window.)
        event = self.event.copy(update={"window": None})
        return MergedWorksSource(
            event=event,
            query=get_related_works_query(related_ids),
            es_client=self.es_client,
        )

    def _get_work_ancestors(self, ids: list[str]) -> dict:
        """Return all ancestors of each work in the current batch."""
        return self.make_neptune_query("work_ancestors", ids)

    def _get_work_children(self, ids: list[str]) -> dict:
        """Return all children of each work in the current batch."""
        return self.make_neptune_query("work_children", ids)

    def _get_work_concepts(self, ids: list[str]) -> dict:
        """Return all concepts of each work in the current batch."""
        return self.make_neptune_query("work_concepts", ids)

    def process_es_works(
        self, es_works: Iterator[MergedWork]
    ) -> Generator[ExtractedWork]:
        for es_batch in batched(es_works, WORKS_BATCH_SIZE):
            # Make graph queries to retrieve ancestors, children, and concepts for all visible works in each batch
            visible_work_ids = [
                w.state.canonical_id
                for w in es_batch
                if isinstance(w, VisibleMergedWork)
            ]
            all_ancestors = self._get_work_ancestors(visible_work_ids)
            all_children = self._get_work_children(visible_work_ids)
            all_concepts = self._get_work_concepts(visible_work_ids)

            for es_work in es_batch:
                work_id = es_work.state.canonical_id
                self.streamed_ids.add(work_id)

                hierarchy = WorkHierarchy(
                    id=work_id,
                    ancestors=all_ancestors.get(work_id, {}).get("ancestors", []),
                    children=all_children.get(work_id, {}).get("children", []),
                )
                concepts = all_concepts.get(work_id, {}).get("concepts", [])

                # When a work is reprocessed, all of its children and ancestors must be reprocessed too for consistency.
                # (For example, if the title of a parent work changes, all of its children must be processed
                # and reindexed to store the new title.)
                self.related_ids.update(
                    c.work.properties.id for c in hierarchy.children
                )
                self.related_ids.update(
                    c.work.properties.id for c in hierarchy.ancestors
                )

                # Only visible works story hierarchy and concepts
                if isinstance(es_work, VisibleMergedWork):
                    yield VisibleExtractedWork(
                        work=es_work, hierarchy=hierarchy, concepts=concepts
                    )
                else:
                    yield ExtractedWork(work=es_work)

    def extract_raw(self) -> Generator[ExtractedWork]:
        works_stream = (
            MergedWork.from_raw_document(w) for w in self.es_source.stream_raw()
        )
        yield from self.process_es_works(works_stream)

        # Before processing related works, filter out works which were already processed above
        related_ids = self.related_ids.difference(self.streamed_ids)
        logger.info("Will process related works", count=len(related_ids))

        related_works_source = self.get_related_works_source(list(related_ids))
        related_works_stream = (
            MergedWork.from_raw_document(w) for w in related_works_source.stream_raw()
        )
        yield from self.process_es_works(related_works_stream)
