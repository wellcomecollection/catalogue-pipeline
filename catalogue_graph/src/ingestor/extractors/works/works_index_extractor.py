from collections.abc import Generator

import structlog
from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from graph.sources.merged_works_source import MergedWorksSource
from ingestor.extractors.base_extractor import StreamingExtractor
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.merged.work import (
    MergedWork,
    VisibleMergedWork,
)
from ingestor.models.neptune.node import WorkNode
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
        self.related_ids: set[str] = set()

    def get_related_works_source(self, related_ids: list[str]) -> MergedWorksSource:
        # Remove `window` from event before retrieving related works. (All related works should be processed
        # even if they aren't part of the current window.)
        event = self.event.copy(update={"window": None})
        return MergedWorksSource(
            event=event,
            query=get_works_by_id_query(related_ids),
            es_client=self.es_client,
        )

    def _process_visible_batch(
        self, visible_works: list[VisibleMergedWork]
    ) -> Generator[VisibleExtractedWork]:
        # When a work is processed in incremental mode, its parent and descendants must be processed too.
        # This is because each work document stores the IDs of all of its ancestors (`partOf` field)
        # and children (`parts` field), along with their titles and reference numbers.

        if self.event.mode_label != "full":
            yield from super()._process_visible_batch(visible_works)

        visible_work_ids = [w.state.canonical_id for w in visible_works]
        descendants_batch = self._get_work_descendants(visible_work_ids)

        for extracted_work in super()._process_visible_batch(visible_works):
            work_id = extracted_work.work.state.canonical_id
            raw_desc = descendants_batch.get(work_id, {}).get("descendants", [])
            descendants = [WorkNode.model_validate(d) for d in raw_desc]
            ancestors = extracted_work.hierarchy.ancestors

            self.related_ids |= {d.properties.id for d in descendants}
            if ancestors:
                self.related_ids.add(ancestors[0].work.properties.id)

            yield extracted_work

    def extract_raw(self) -> Generator[ExtractedWork]:
        works_stream = (
            MergedWork.from_raw_document(w) for w in self.es_source.stream_raw()
        )
        yield from self.process_es_works(works_stream)

        # Before processing related works, filter out works which were already processed above.
        related_ids = self.related_ids.difference(self.streamed_ids)
        logger.info("Will process related works", count=len(related_ids))

        related_works_source = self.get_related_works_source(list(related_ids))
        related_works_stream = (
            MergedWork.from_raw_document(w) for w in related_works_source.stream_raw()
        )
        yield from self.process_es_works(related_works_stream)
