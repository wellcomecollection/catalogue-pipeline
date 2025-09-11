from collections.abc import Generator
from itertools import batched

from models.events import IncrementalWindow
from pydantic import BaseModel
from sources.catalogue.merged_works_source import MergedWorksSource
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


class GraphWorksExtractor:
    def __init__(
        self, pipeline_date: str, window: IncrementalWindow = None, is_local: bool = False
    ):
        self.neptune_client = get_neptune_client(is_local)
        self.pipeline_date = pipeline_date
        # {"match": {"type": "Visible"}}
        self.source = MergedWorksSource(pipeline_date=pipeline_date, window=window, is_local=is_local)

    def make_neptune_query(self, query: str, ids: list[str], label: str) -> list[dict]:
        return list(self.neptune_client.get_in_parallel(query, ids, label))

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

    def extract_raw(self) -> Generator[ExtractedWork]:
        streamed_ids = set()
        related_ids = set()
        
        es_documents = self.source.stream_raw()
        for batch in batched(es_documents, 10_000, strict=False):
            es_works = [BaseDenormalisedWork.from_es_document(doc) for doc in batch]
            
            visible_ids = [w.state.canonical_id for w in es_works if w.type == "Visible"]
            all_ancestors = self._get_work_ancestors(visible_ids)
            all_children = self._get_work_children(visible_ids)
            all_concepts = self._get_work_concepts(visible_ids)
            
            for es_work in es_works:
                work_id = es_work.state.canonical_id
                streamed_ids.add(work_id)

                work_hierarchy = WorkHierarchy(
                    id=work_id,
                    ancestors=all_ancestors.get(work_id, {}).get("ancestors", []),
                    children=all_children.get(work_id, {}).get("children", []),
                )

                related_ids.update(set(c.work.properties.id for c in work_hierarchy.children))
                related_ids.update(set(c.work.properties.id for c in work_hierarchy.ancestors))

                work_concepts = []
                for raw_concept in all_concepts.get(work_id, []):
                    work_concepts.append(WorkConcept(**raw_concept))
    
                yield ExtractedWork(
                    work=es_work,
                    hierarchy=work_hierarchy,
                    concepts=work_concepts,
                )
        
        print(len(related_ids))
        print(len(related_ids.difference(streamed_ids)))
