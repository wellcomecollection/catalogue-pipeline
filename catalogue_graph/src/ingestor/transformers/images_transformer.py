from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from ingestor.extractors.images_extractor import ExtractedImage, GraphImagesExtractor
from ingestor.models.indexable_image import IndexableImage
from models.events import BasePipelineEvent

from .base_transformer import IngestorBaseTransformer


class IngestorImagesTransformer(IngestorBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ) -> None:
        super().__init__(GraphImagesExtractor(event, es_client, neptune_client))

    def transform_document(self, extracted: ExtractedImage) -> IndexableImage:
        return IndexableImage.from_extracted_image(extracted)
