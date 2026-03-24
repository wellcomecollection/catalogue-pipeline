from collections.abc import Generator, Iterator
from itertools import batched

import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from clients.neptune_client import NeptuneClient
from graph.sources.augmented_images_source import AugmentedImagesSource
from ingestor.models.augmented.image import AugmentedImage
from ingestor.models.neptune.query_result import ExtractedConcept
from models.events import BasePipelineEvent

from .base_extractor import GraphBaseExtractor
from .works_extractor import WorkConceptIdExtractor

logger = structlog.get_logger(__name__)

IMAGES_BATCH_SIZE = 2_000


class ExtractedImage(BaseModel):
    image: AugmentedImage
    concepts: list[ExtractedConcept]


class GraphImagesExtractor(GraphBaseExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(neptune_client)
        self.es_source = AugmentedImagesSource(
            event,
            es_client=es_client,
        )
        self.event = event
        self.es_client = es_client

        self.concept_id_extractor = WorkConceptIdExtractor(neptune_client)

        self.extracted_concepts: dict[str, ExtractedConcept] = {}

    def process_es_images(
        self, es_images: Iterator[AugmentedImage]
    ) -> Generator[ExtractedImage]:
        for es_batch in batched(es_images, IMAGES_BATCH_SIZE):
            work_data = {i.source.id.canonical_id: i.source.data for i in es_batch}
            concepts_batch = self.concept_id_extractor.get_work_concepts(work_data)

            for es_image in es_batch:
                work_id = es_image.source.id.canonical_id
                yield ExtractedImage(image=es_image, concepts=concepts_batch[work_id])

    def extract_raw(self) -> Generator[ExtractedImage]:
        images_stream = (
            AugmentedImage.model_validate(i) for i in self.es_source.stream_raw()
        )
        yield from self.process_es_images(images_stream)
