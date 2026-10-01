from collections.abc import Generator
from itertools import batched
from typing import Any

import structlog
from elasticsearch import Elasticsearch

from graph.sources.merged_works_source import MergedWorksSource
from graph.transformers.catalogue.raw_work import RawCatalogueWork
from models.events import BasePipelineEvent, PipelinePitIds

logger = structlog.get_logger(__name__)


COLLECTION_PATH_KEYWORD_FIELD = "data.collectionPath.path.keyword"
MAX_BOOL_CLAUSES = 512


def _regexp_literal(value: str) -> str:
    # Quoting stops Lucene reading '.', '&', '|' etc. as operators; a quote can only be escaped outside quotes
    return '"' + value.lower().replace('"', '"\\""') + '"'


def child_path_prefixes(work: dict) -> set[str]:
    # Children's paths start with the parent's full path or its path identifier. The latter covers Axiell
    # with AXIELL_COLLECTION_PATH_SOURCE=part_of, where a child's path is '<parent key>/<own key>' from the 982
    raw_work = RawCatalogueWork(work)
    return {p for p in (raw_work.path, raw_work.path_identifier) if p}


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

    def _get_child_source(self, path_prefixes: set[str]) -> MergedWorksSource:
        child_clauses = [
            {
                "regexp": {
                    COLLECTION_PATH_KEYWORD_FIELD: f"{_regexp_literal(prefix)}/[^/]+"
                }
            }
            for prefix in path_prefixes
        ]

        child_query = {"bool": {"should": child_clauses, "minimum_should_match": 1}}
        full_query = {"bool": {"must": [self.base_query, child_query]}}

        unscoped_event = self.event.model_copy(update={"window": None, "ids": None})
        unscoped_event.pit_ids = PipelinePitIds(merged=self.pit_id)
        return MergedWorksSource(
            unscoped_event,
            es_client=self.es_client,
            query=full_query,
            fields=self.fields,
            slice_count=1,  # Expected child set is small
        )

    def stream_raw(self) -> Generator[Any]:
        seen_ids: set[str] = set()
        path_prefixes: set[str] = set()

        for work in super().stream_raw():
            work_id: str = work["state"]["canonicalId"]
            seen_ids.add(work_id)
            path_prefixes |= child_path_prefixes(work)
            yield work

        if not path_prefixes:
            return

        logger.info(
            "Querying for children of streamed works",
            path_prefix_count=len(path_prefixes),
        )

        child_count = 0
        # Split path prefixes into batches so that we don't exceed Elasticsearch's max_clause_count limit
        for batch in batched(path_prefixes, MAX_BOOL_CLAUSES):
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
