import re
from collections.abc import Generator
from typing import Any

import structlog
from elasticsearch import Elasticsearch

from graph.sources.merged_works_source import MergedWorksSource
from models.events import BasePipelineEvent

logger = structlog.get_logger(__name__)

COLLECTION_PATH_FIELD = "data.collectionPath.path"


def _escape_elasticsearch_regexp(value: str) -> str:
    """Escape special characters for Elasticsearch's regexp query syntax."""
    return re.sub(r'([.?+*|{}()\[\]"\\])', r"\\\1", value)


class MergedWorksWithChildrenSource(MergedWorksSource):
    """A source that streams works matching the event scope, then also
    retrieves direct children of those works based on their collection paths.

    This ensures that when a work is added to the graph, edges between its
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
        self.query = query
        self.event = event

    def stream_raw(self) -> Generator[Any]:
        seen_ids: set[str] = set()
        collection_paths: set[str] = set()

        for work in super().stream_raw():
            work_id: str = work["state"]["canonicalId"]
            seen_ids.add(work_id)

            path: str | None = (
                work.get("data", {}).get("collectionPath", {}).get("path")
            )
            if path:
                collection_paths.add(path.rstrip("/"))

            yield work

        if not collection_paths:
            return

        logger.info(
            "Querying for children of streamed works",
            collection_path_count=len(collection_paths),
        )

        child_regexp_clauses = [
            {
                "regexp": {
                    COLLECTION_PATH_FIELD: f"{_escape_elasticsearch_regexp(path)}/[^/]+"
                }
            }
            for path in collection_paths
        ]

        child_query_clauses: list[dict] = []
        if self.query:
            child_query_clauses.append(self.query)

        child_query: dict = {
            "bool": {
                "must": child_query_clauses,
                "should": child_regexp_clauses,
                "minimum_should_match": 1,
            }
        }

        unscoped_event = self.event.model_copy(
            update={"window": None, "ids": None}
        )

        child_source = MergedWorksSource(
            unscoped_event,
            es_client=self.es_client,
            query=child_query,
            fields=self.fields,
        )

        child_count = 0
        for work in child_source.stream_raw():
            work_id = work["state"]["canonicalId"]
            if work_id not in seen_ids:
                seen_ids.add(work_id)
                child_count += 1
                yield work

        logger.info(
            "Finished streaming children of scoped works",
            child_count=child_count,
        )
