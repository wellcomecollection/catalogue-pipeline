from ingestor.extractors.works_extractor import (
    ExtractedWork,
    GraphWorksExtractor,
)
from ingestor.models.indexable_work import (
    IndexableWork,
)
from models.events import BasePipelineEvent
from utils.elasticsearch import ElasticsearchMode

from .base_transformer import ElasticsearchBaseTransformer


class ElasticsearchWorksTransformer(ElasticsearchBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_mode: ElasticsearchMode,
    ) -> None:
        self.source = GraphWorksExtractor(event, es_mode)

    def transform_document(self, extracted: ExtractedWork) -> IndexableWork:
        return IndexableWork.from_extracted_work(extracted)
