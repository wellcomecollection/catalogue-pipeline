from collections.abc import Generator
from queue import Queue

from models.events import IncrementalWindow
from pydantic import BaseModel
from sources.threaded_es_source import ThreadedElasticsearchSource
from utils.aws import get_neptune_client

from ingestor.models.denormalised.work import BaseDenormalisedWork, DenormalisedWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
)


class ExtractedWork(BaseModel):
    work: DenormalisedWork
    hierarchy: WorkHierarchy
    concepts: list[WorkConcept]


class GraphWorksExtractor(ThreadedElasticsearchSource):
    def __init__(
        self, pipeline_date: str, window: IncrementalWindow = None, is_local: bool = False
    ):
        super().__init__(pipeline_date=pipeline_date, window=window, is_local=is_local)
        self.neptune_client = get_neptune_client(is_local)

    def make_neptune_query(self, query: str, ids: list[str], label: str) -> list[dict]:
        return list(self.neptune_client.time_open_cypher_query(query, {"ids": ids}, label))

    def _get_work_ancestors(self, ids: list[str]) -> dict:
        """Return all ancestors of each work in the current batch."""
        results = self.make_neptune_query(WORK_ANCESTORS_QUERY, ids, "work ancestors")
        return {item["id"]: item for item in results}

    def _get_work_children(self, ids: list[str]) -> dict:
        """Return all children of each work in the current batch."""
        results = self.make_neptune_query(WORK_CHILDREN_QUERY, ids, "work children")
        return {item["id"]: item for item in results}

    def _get_work_concepts(self, ids: list[str]) -> dict:
        """Return all concepts of each work in the current batch."""
        results = self.make_neptune_query(WORK_CONCEPTS_QUERY, ids, "work concepts")
        return {item["id"]: item["concepts"] for item in results}

    def worker_target(self, slice_index: int, queue: Queue) -> None:
        search_after = None
        while hits := self.search(slice_index, search_after):
            es_works = [BaseDenormalisedWork.from_es_document(hit["_source"]) for hit in hits]

            visible_ids = [w.state.canonical_id for w in es_works if w.type == "Visible"]
            all_ancestors = self._get_work_ancestors(visible_ids)
            all_children = self._get_work_children(visible_ids)
            all_concepts = self._get_work_concepts(visible_ids)

            for es_work in es_works:
                work_id = es_work.state.canonical_id
                
                work_hierarchy = WorkHierarchy(
                    id=work_id,
                    ancestors=all_ancestors.get(work_id, {}).get("ancestors", []),
                    children=all_children.get(work_id, {}).get("children", []),
                )

                work_concepts = []
                for raw_concept in all_concepts.get(work_id, []):
                    work_concepts.append(WorkConcept(**raw_concept))

                queue.put(ExtractedWork(
                    work=es_work,
                    hierarchy=work_hierarchy,
                    concepts=work_concepts,
                ))
                
            search_after = hits[-1]["sort"]
    
        queue.put(None)

    def extract_raw(self) -> Generator[ExtractedWork]:
        streamed_ids = set()
        related_ids = set()
            
        for item in self.stream_raw():
            streamed_ids.add(item.work.state.canonical_id)
            related_ids.update(c.work.properties.id for c in item.hierarchy.children)
            related_ids.update(c.work.properties.id for c in item.hierarchy.ancestors)
            
            yield item

        print(len(related_ids))
        print(len(related_ids.difference(streamed_ids)))
