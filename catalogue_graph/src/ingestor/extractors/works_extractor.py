import time
from collections.abc import Generator

from pydantic import BaseModel

import config
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
    WORK_QUERY,
)
from utils.elasticsearch import get_client, get_standard_index_name

from .base_extractor import GraphBaseExtractor


class ExtractedWork(BaseModel):
    work: DenormalisedWork
    hierarchy: WorkHierarchy
    concepts: list[WorkConcept]


class GraphWorksExtractor(GraphBaseExtractor):
    def __init__(
        self, pipeline_date: str, start_offset: int, end_index: int, is_local: bool
    ):
        super().__init__(start_offset, end_index, is_local)
        self.pipeline_date = pipeline_date

    def get_es_works(self, work_ids: list[str]) -> dict:
        es_client = get_client("graph_extractor", self.pipeline_date, True)
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, self.pipeline_date
        )

        start_time = time.time()
        result = es_client.mget(index=index_name, body={"ids": work_ids})
        duration = round(time.time() - start_time)

        work_mapping = {}
        for work in result["docs"]:
            work_id = work["_id"]
            if not work["found"]:
                print(f"Work {work_id} does not exist in the denormalised index.")
                continue

            work_mapping[work_id] = work["_source"]

        print(
            f"Ran 'es works' query in {duration} seconds, retrieving {len(work_mapping)} records."
        )
        return work_mapping

    def _get_work_ids(self) -> Generator[str]:
        """Return a list of all work IDs belonging to the current batch."""
        for item in self.make_neptune_query(WORK_QUERY, "works"):
            yield item["id"]

    def _get_work_ancestors(self) -> dict:
        """Return all ancestors of each work in the current batch."""
        results = self.make_neptune_query(WORK_ANCESTORS_QUERY, "work ancestors")
        return {item["id"]: item for item in results}

    def _get_work_children(self) -> dict:
        """Return all children of each work in the current batch."""
        results = self.make_neptune_query(WORK_CHILDREN_QUERY, "work children")
        return {item["id"]: item for item in results}

    def _get_work_concepts(self) -> dict:
        """Return all concepts of each work in the current batch."""
        results = self.make_neptune_query(WORK_CONCEPTS_QUERY, "work concepts")
        return {item["id"]: item["concepts"] for item in results}

    def extract_raw(
        self,
    ) -> Generator[ExtractedWork]:
        work_ids = list(self._get_work_ids())
        all_ancestors = self._get_work_ancestors()
        all_children = self._get_work_children()
        all_concepts = self._get_work_concepts()
        all_es_works = self.get_es_works(work_ids)

        for work_id in work_ids:
            es_work = all_es_works[work_id]

            work_hierarchy = WorkHierarchy(
                id=work_id,
                ancestor_works=all_ancestors.get(work_id, {}).get("ancestor_works", []),
                children=all_children.get(work_id, {}).get("children", []),
            )

            work_concepts = []
            for raw_concept in all_concepts.get(work_id, []):
                work_concepts.append(WorkConcept(**raw_concept))

            yield ExtractedWork(
                work=DenormalisedWork(**es_work),
                hierarchy=work_hierarchy,
                concepts=work_concepts,
            )
