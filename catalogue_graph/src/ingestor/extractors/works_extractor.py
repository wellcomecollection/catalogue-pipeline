import time
from collections.abc import Generator

from pydantic import BaseModel

import config
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy
from ingestor.queries.work_queries import (
    WORK_CONCEPTS_QUERY,
    WORK_HIERARCHY_QUERY,
    WORK_QUERY,
)
from utils.elasticsearch import get_client, get_standard_index_name

from .base_extractor import GraphBaseExtractor


class ExtractedWork(BaseModel):
    work: DenormalisedWork
    hierarchy: WorkHierarchy
    concepts: list[WorkConcept]


class GraphWorksExtractor(GraphBaseExtractor):
    def get_es_works(self, work_ids: list[str]) -> dict:
        es_client = get_client("graph_extractor", "2025-05-01", True)
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, "2025-05-01"
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
        for item in self.make_neptune_query(WORK_QUERY, "works"):
            yield item["id"]

    def _get_work_hierarchy(self) -> dict:
        results = self.make_neptune_query(WORK_HIERARCHY_QUERY, "work hierarchy")
        return {item["id"]: item for item in results}

    def _get_work_concepts(self) -> dict:
        results = self.make_neptune_query(WORK_CONCEPTS_QUERY, "work concepts")
        return {item["id"]: item["concepts"] for item in results}

    def extract_raw(
        self,
    ) -> Generator[ExtractedWork]:
        work_ids = list(self._get_work_ids())
        all_hierarchy = self._get_work_hierarchy()
        all_concepts = self._get_work_concepts()
        all_es_works = self.get_es_works(work_ids)

        for work_id in work_ids:
            es_work = all_es_works[work_id]

            work_hierarchy = WorkHierarchy(id=work_id)
            if all_hierarchy.get(work_id) is not None:
                work_hierarchy = WorkHierarchy(**all_hierarchy[work_id])

            work_concepts = []
            for raw_concept in all_concepts.get(work_id, []):
                work_concepts.append(WorkConcept(**raw_concept))

            yield ExtractedWork(
                work=DenormalisedWork(**es_work),
                hierarchy=work_hierarchy,
                concepts=work_concepts,
            )
