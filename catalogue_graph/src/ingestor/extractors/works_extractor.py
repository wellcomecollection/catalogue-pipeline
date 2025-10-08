from collections.abc import Generator, Iterator
from itertools import batched

from pydantic import BaseModel

from ingestor.models.denormalised.work import (
    DenormalisedWork,
    VisibleDenormalisedWork,
)
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from models.events import BasePipelineEvent
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode

from .base_extractor import GraphBaseExtractor

WORKS_BATCH_SIZE = 10_000


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
    work: DenormalisedWork


class VisibleExtractedWork(ExtractedWork):
    work: VisibleDenormalisedWork
    hierarchy: WorkHierarchy
    concepts: list[WorkConcept]


class GraphWorksExtractor(GraphBaseExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_mode: ElasticsearchMode,
    ):
        super().__init__(es_mode != "private")
        self.es_source = MergedWorksSource(
            event,
            es_mode=es_mode,
        )
        self.event = event
        self.es_mode = es_mode

        self.streamed_ids: set[str] = set()
        self.related_ids: set[str] = set()

    def get_related_works_source(self, related_ids: list[str]) -> MergedWorksSource:
        # Remove `window` from event before retrieving related works. (All related works should be processed
        # even if they aren't part of the current window.)
        event = BasePipelineEvent(
            pipeline_date=self.event.pipeline_date, pit_id=self.event.pit_id
        )
        return MergedWorksSource(
            event=event,
            query=get_related_works_query(related_ids),
            es_mode=self.es_mode,
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
        self, es_works: Iterator[DenormalisedWork]
    ) -> Generator[ExtractedWork]:
        for es_batch in batched(es_works, WORKS_BATCH_SIZE, strict=False):
            # Make graph queries to retrieve ancestors, children, and concepts for all visible works in each batch
            visible_work_ids = [
                w.state.canonical_id
                for w in es_batch
                if isinstance(w, VisibleDenormalisedWork)
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
                if isinstance(es_work, VisibleDenormalisedWork):
                    yield VisibleExtractedWork(
                        work=es_work, hierarchy=hierarchy, concepts=concepts
                    )
                else:
                    yield ExtractedWork(work=es_work)

    def extract_raw(self) -> Generator[ExtractedWork]:
        works_stream = (
            DenormalisedWork.from_raw_document(w) for w in self.es_source.stream_raw()
        )
        yield from self.process_es_works(works_stream)

        # Before processing related works, filter out works which were already processed above
        related_ids = self.related_ids.difference(self.streamed_ids)
        print(f"Will process a total of {len(related_ids)} related works.")

        related_works_source = self.get_related_works_source(list(related_ids))
        related_works_stream = (
            DenormalisedWork.from_raw_document(w)
            for w in related_works_source.stream_raw()
        )
        yield from self.process_es_works(related_works_stream)
