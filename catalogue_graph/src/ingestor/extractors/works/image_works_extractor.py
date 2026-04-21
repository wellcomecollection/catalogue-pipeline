from collections.abc import Iterable

import structlog
from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from graph.sources.merged_works_source import MergedWorksSource
from ingestor.models.merged.work import (
    MergedWork,
)
from models.events import BasePipelineEvent

from .base_works_extractor import (
    GraphBaseWorksExtractor,
    VisibleExtractedWork,
    get_works_by_id_query,
)

logger = structlog.get_logger(__name__)


class ImageWorksExtractor(GraphBaseWorksExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(neptune_client)
        self.es_client = es_client
        self.event = event

    def extract_batch(self, work_ids: Iterable[str]) -> dict[str, VisibleExtractedWork]:
        es_source = MergedWorksSource(
            event=self.event.copy(update={"window": None, "ids": None}),
            query=get_works_by_id_query(list(work_ids)),
            es_client=self.es_client,
        )

        works_stream = (MergedWork.from_raw_document(w) for w in es_source.stream_raw())
        extracted_works = self.process_es_works(works_stream)
        visible_extracted_works = [
            w for w in extracted_works if isinstance(w, VisibleExtractedWork)
        ]
        return {w.work.state.canonical_id: w for w in visible_extracted_works}
