from collections.abc import Generator, Iterator
from itertools import batched

from pydantic import BaseModel

from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from models.events import IncrementalWindow
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode

from .base_extractor import GraphBaseExtractor

ES_QUERY = {"match": {"type": "Visible"}}
WORKS_BATCH_SIZE = 10_000


class ExtractedWork(BaseModel):
    work: DenormalisedWork
    hierarchy: WorkHierarchy
    concepts: list[WorkConcept]


class GraphWorksExtractor(GraphBaseExtractor):
    def __init__(
        self,
        pipeline_date: str,
        window: IncrementalWindow | None,
        es_mode: ElasticsearchMode,
    ):
        super().__init__(es_mode != "private")
        self.es_source = MergedWorksSource(
            pipeline_date=pipeline_date,
            window=window,
            es_mode=es_mode,
            query=ES_QUERY,
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
            visible_work_ids = [
                w.state.canonical_id for w in es_batch if w.type == "Visible"
            ]
            all_ancestors = self._get_work_ancestors(visible_work_ids)
            all_children = self._get_work_children(visible_work_ids)
            all_concepts = self._get_work_concepts(visible_work_ids)

            for es_work in es_batch:
                work_id = es_work.state.canonical_id
                yield ExtractedWork(
                    work=es_work,
                    hierarchy=WorkHierarchy(
                        id=work_id,
                        ancestors=all_ancestors.get(work_id, {}).get("ancestors", []),
                        children=all_children.get(work_id, {}).get("children", []),
                    ),
                    concepts=all_concepts.get(work_id, {}).get("concepts", []),
                )

    def extract_raw(self) -> Generator[ExtractedWork]:
        streamed_ids: set[str] = set()
        related_ids: set[str] = set()

        works_stream = (DenormalisedWork(**w) for w in self.es_source.stream_raw())
        for extracted_work in self.process_es_works(works_stream):
            streamed_ids.add(extracted_work.work.state.canonical_id)

            # When a work is reprocessed, all of its children and ancestors must be reprocessed too for consistency.
            related_ids.update(
                c.work.properties.id for c in extracted_work.hierarchy.children
            )
            related_ids.update(
                c.work.properties.id for c in extracted_work.hierarchy.ancestors
            )

            yield extracted_work

        # Only process related works which were not already processed above
        related_ids = related_ids.difference(streamed_ids)
        print(f"Will process a total of {len(related_ids)} related works.")
        related_works_stream = (
            DenormalisedWork(**w) for w in self.es_source.mget(list(related_ids))
        )
        yield from self.process_es_works(related_works_stream)
