from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from ingestor.extractors.works_extractor import (
    ExtractedWork,
    GraphWorksExtractor,
    VisibleExtractedWork,
)
from ingestor.models.indexable_work import (
    DeletedIndexableWork,
    IndexableWork,
    InvisibleIndexableWork,
    RedirectedIndexableWork,
    VisibleIndexableWork,
)
from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    RedirectedMergedWork,
)
from models.events import BasePipelineEvent

from .base_transformer import ElasticsearchBaseTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ) -> None:
        self.source = GraphWorksExtractor(event, es_client, neptune_client)

    def transform_document(self, extracted: ExtractedWork) -> IndexableWork:
        work = extracted.work
        if isinstance(extracted, VisibleExtractedWork):
            return VisibleIndexableWork.from_extracted_work(extracted)
        if isinstance(work, RedirectedMergedWork):
            return RedirectedIndexableWork.from_merged_work(work)
        if isinstance(work, DeletedMergedWork):
            return DeletedIndexableWork.from_merged_work(work)
        if isinstance(work, InvisibleMergedWork):
            return InvisibleIndexableWork.from_merged_work(work)

        raise TypeError(
            f"Unknown work type '{type(extracted.work)}' for work {extracted.work}"
        )
