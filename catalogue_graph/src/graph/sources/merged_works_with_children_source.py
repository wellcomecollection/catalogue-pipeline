from collections.abc import Generator
from typing import Any

import structlog
from elasticsearch import Elasticsearch

from graph.sources.merged_works_source import MergedWorksSource
from models.events import BasePipelineEvent

logger = structlog.get_logger(__name__)


COLLECTION_PATH_KEYWORD_FIELD = "data.collectionPath.path.keyword"


class MergedWorksWithChildrenSource(MergedWorksSource):
    """A source that streams works matching the event scope, then also
    retrieves direct children of those works based on their collection paths.

    This ensures that when a work is added to an existing hierarchy, edges between its
    children's path identifiers and its own path identifier are also created.
    """

    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
    ):
        super().__init__(event, es_client=es_client, query=query, fields=fields)
        self.event = event

    def _get_child_source(self, collection_paths: set) -> MergedWorksSource:
        child_clauses = []
        for path in collection_paths:
            quoted_path = f'"{path.lower()}"'
            child_clauses.append(
                {"regexp": {COLLECTION_PATH_KEYWORD_FIELD: f"{quoted_path}/[^/]+"}}
            )
        unscoped_event = self.event.model_copy(update={"window": None, "ids": None})
        return MergedWorksSource(
            unscoped_event,
            es_client=self.es_client,
            query={"bool": {"should": child_clauses}},
            fields=self.fields,
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
        for work in self._get_child_source(collection_paths).stream_raw():
            work_id = work["state"]["canonicalId"]
            if work_id not in seen_ids:
                seen_ids.add(work_id)
                child_count += 1
                yield work

        logger.info(
            "Finished streaming children of scoped works",
            child_count=child_count,
        )
