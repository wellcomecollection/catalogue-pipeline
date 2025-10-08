from ingestor.extractors.works_extractor import (
    ExtractedWork,
    GraphWorksExtractor,
    VisibleExtractedWork,
)
from ingestor.models.denormalised.work import (
    DeletedDenormalisedWork,
    InvisibleDenormalisedWork,
    RedirectedDenormalisedWork,
)
from ingestor.models.indexable_work import (
    DeletedIndexableWork,
    IndexableWork,
    InvisibleIndexableWork,
    RedirectedIndexableWork,
    VisibleIndexableWork,
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
        work = extracted.work
        if isinstance(extracted, VisibleExtractedWork):
            return VisibleIndexableWork.from_extracted_work(extracted)
        if isinstance(work, RedirectedDenormalisedWork):
            return RedirectedIndexableWork.from_denormalised_work(work)
        if isinstance(work, DeletedDenormalisedWork):
            return DeletedIndexableWork.from_denormalised_work(work)
        if isinstance(work, InvisibleDenormalisedWork):
            return InvisibleIndexableWork.from_denormalised_work(work)

        raise TypeError(
            f"Unknown work type '{type(extracted.work)}' for work {extracted.work}"
        )
