from collections.abc import Generator
from itertools import batched

from pydantic import BaseModel

from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from models.events import IncrementalWindow
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode

from .base_extractor import GraphBaseExtractor

ES_QUERY = {"match": {"type": "Visible"}}


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

    def get_works(self) -> Generator[DenormalisedWork]:
        for work in self.es_source.stream_raw():
            yield DenormalisedWork(**work)

    def get_work_stream(self) -> Generator[tuple[DenormalisedWork]]:
        yield from batched(self.get_works(), 10_000, strict=False)

    def extract_raw(self) -> Generator[ExtractedWork]:
        streamed_ids: set[str] = set()
        related_ids: set[str] = set()

        for es_works in self.get_work_stream():
            visible_work_ids = [
                w.state.canonical_id for w in es_works if w.type == "Visible"
            ]
            all_ancestors = self._get_work_ancestors(visible_work_ids)
            all_children = self._get_work_children(visible_work_ids)
            all_concepts = self._get_work_concepts(visible_work_ids)

            for es_work in es_works:
                work_id = es_work.state.canonical_id

                work_hierarchy = WorkHierarchy(
                    id=work_id,
                    ancestors=all_ancestors.get(work_id, {}).get("ancestors", []),
                    children=all_children.get(work_id, {}).get("children", []),
                )

                work_concepts = []
                for raw_concept in all_concepts.get(work_id, {}).get("concepts", []):
                    work_concepts.append(WorkConcept(**raw_concept))

                streamed_ids.add(es_work.state.canonical_id)
                related_ids.update(
                    c.work.properties.id for c in work_hierarchy.children
                )
                related_ids.update(
                    c.work.properties.id for c in work_hierarchy.ancestors
                )

                yield ExtractedWork(
                    work=es_work,
                    hierarchy=work_hierarchy,
                    concepts=work_concepts,
                )
