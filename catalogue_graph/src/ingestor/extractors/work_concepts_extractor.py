from collections.abc import Generator, Iterable

import structlog

from clients.neptune_client import NeptuneClient
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
)

from .base_concepts_extractor import GraphBaseConceptsExtractor

logger = structlog.get_logger(__name__)


class GraphWorkConceptsExtractor(GraphBaseConceptsExtractor):
    def __init__(
        self,
        neptune_client: NeptuneClient,
        concept_ids: Iterable[str],
    ):
        super().__init__(neptune_client)
        self.concept_ids = concept_ids

    def get_concept_ids_to_process(self) -> Generator[str]:
        yield from self.concept_ids

    def extract_raw(self) -> Generator[tuple[str, ExtractedConcept]]:
        for concept_ids in self.get_consistent_batches():
            logger.info("Processing batch of concepts", count=len(concept_ids))
            yield from self.get_concepts(concept_ids).items()
