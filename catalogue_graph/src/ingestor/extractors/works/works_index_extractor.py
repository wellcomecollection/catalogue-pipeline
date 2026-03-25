from collections.abc import Generator

import structlog
from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from graph.sources.merged_works_source import MergedWorksSource
from ingestor.extractors.base_extractor import StreamingExtractor
from ingestor.models.merged.work import (
    MergedWork,
)
from models.events import BasePipelineEvent

from .base_works_extractor import (
    ExtractedWork,
    GraphBaseWorksExtractor,
    get_works_by_id_query,
)

logger = structlog.get_logger(__name__)


class WorksIndexExtractor(GraphBaseWorksExtractor, StreamingExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(neptune_client)
        self.es_source = MergedWorksSource(
            event,
            es_client=es_client,
        )
        self.event = event
        self.es_client = es_client

    def get_related_works_source(self, related_ids: list[str]) -> MergedWorksSource:
        # Remove `window` from event before retrieving related works. (All related works should be processed
        # even if they aren't part of the current window.)
        event = self.event.copy(update={"window": None})
        return MergedWorksSource(
            event=event,
            query=get_works_by_id_query(related_ids),
            es_client=self.es_client,
        )

    def extract_raw(self) -> Generator[ExtractedWork]:
        works_stream = (
            MergedWork.from_raw_document(w) for w in self.es_source.stream_raw()
        )
        yield from self.process_es_works(works_stream)

        # When a work is processed, all of its children and ancestors must be processed too for consistency.
        # (For example, if the title of a parent work changes, all of its children must be processed
        # and reindexed to store the new title.)
        # Before processing related works, filter out works which were already processed above.
        related_ids = self.related_ids.difference(self.streamed_ids)
        logger.info("Will process related works", count=len(related_ids))

        related_works_source = self.get_related_works_source(list(related_ids))
        related_works_stream = (
            MergedWork.from_raw_document(w) for w in related_works_source.stream_raw()
        )
        yield from self.process_es_works(related_works_stream)
