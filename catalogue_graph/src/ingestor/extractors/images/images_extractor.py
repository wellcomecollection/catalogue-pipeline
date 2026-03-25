from collections.abc import Generator, Iterator
from itertools import batched

import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from clients.neptune_client import NeptuneClient
from graph.sources.augmented_images_source import AugmentedImagesSource
from ingestor.extractors.base_extractor import GraphBaseExtractor, StreamingExtractor
from ingestor.extractors.works.base_works_extractor import (
    VisibleExtractedWork,
)
from ingestor.extractors.works.image_works_extractor import (
    ImageWorksExtractor,
)
from ingestor.models.augmented.image import AugmentedImage
from models.events import BasePipelineEvent

logger = structlog.get_logger(__name__)

IMAGES_BATCH_SIZE = 2_000


class ExtractedImage(BaseModel):
    image: AugmentedImage
    work: VisibleExtractedWork


class GraphImagesExtractor(GraphBaseExtractor, StreamingExtractor):
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

        self.works_extractor = ImageWorksExtractor(event, es_client, neptune_client)

    def process_es_images(
        self, es_images: Iterator[AugmentedImage]
    ) -> Generator[ExtractedImage]:
        for es_batch in batched(es_images, IMAGES_BATCH_SIZE):
            work_ids = {i.source.id.canonical_id for i in es_batch}
            works_batch = self.works_extractor.extract_batch(work_ids)

            for es_image in es_batch:
                work_id = es_image.source.id.canonical_id
                if work_id in works_batch:
                    yield ExtractedImage(image=es_image, work=works_batch[work_id])
                else:
                    logger.warning(
                        "Image does not have a corresponding visible work",
                        image_id=es_image.state.canonical_id,
                    )

    def extract_raw(self) -> Generator[ExtractedImage]:
        images_stream = (
            AugmentedImage.model_validate(i) for i in self.es_source.stream_raw()
        )
        yield from self.process_es_images(images_stream)
