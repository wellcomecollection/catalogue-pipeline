from collections.abc import Generator
from itertools import batched
from typing import Any

import structlog
from elasticsearch import Elasticsearch

from graph.sources.merged_works_source import MergedWorksSource
from models.events import BasePipelineEvent

logger = structlog.get_logger(__name__)


COLLECTION_PATH_KEYWORD_FIELD = "data.collectionPath.path.keyword"
MAX_BOOL_CLAUSES = 512


class MergedWorksWithChildrenSource(MergedWorksSource):
    """
    A source that streams works matching the event scope, then also
    retrieves direct children of those works based on their collection paths.
    """

    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
    ):
        super().__init__(event, es_client=es_client, query=query, fields=fields)
        self.base_query = query
        self.event = event

    def _get_child_source(self, collection_paths: set[str]) -> MergedWorksSource:
        child_clauses = []
        for path in collection_paths:
            # Path needs to be enclosed in double quotes to escape special characters (e.g. '.')
            quoted_path = f'"{path.lower()}"'
            child_clauses.append(
                {"regexp": {COLLECTION_PATH_KEYWORD_FIELD: f"{quoted_path}/[^/]+"}}
            )

        child_query = {"bool": {"should": child_clauses, "minimum_should_match": 1}}
        full_query = {"bool": {"must": [self.base_query, child_query]}}

        unscoped_event = self.event.model_copy(
            update={"window": None, "ids": None, "pit_id": self.pit_id}
        )
        return MergedWorksSource(
            unscoped_event,
            es_client=self.es_client,
            query=full_query,
            fields=self.fields,
            slice_count=1,  # Expected child set is small
        )

    def stream_raw(self) -> Generator[Any]:
        seen_ids: set[str] = set()
        collection_paths: set[str] = set()

        for work in super().stream_raw():
            work_id: str = work["state"]["canonicalId"]
            seen_ids.add(work_id)

            work_data = work.get("data", {})
            path: str | None = work_data.get("collectionPath", {}).get("path")
            if path:
                collection_paths.add(path.rstrip("/"))

            yield work

        if not collection_paths:
            return

        logger.info(
            "Querying for children of streamed works",
            collection_path_count=len(collection_paths),
        )

        child_count = 0
        # Split collection paths into batches so that we don't exceed Elasticsearch's max_clause_count limit
        for batch in batched(collection_paths, MAX_BOOL_CLAUSES):
            for work in self._get_child_source(set(batch)).stream_raw():
                work_id = work["state"]["canonicalId"]
                if work_id not in seen_ids:
                    seen_ids.add(work_id)
                    child_count += 1
                    yield work

        logger.info(
            "Finished streaming children of scoped works",
            child_count=child_count,
        )
