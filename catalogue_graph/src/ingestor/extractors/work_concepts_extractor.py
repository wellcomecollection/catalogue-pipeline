from collections.abc import Generator, Iterable

import structlog
from clients.neptune_client import NeptuneClient
from elasticsearch import Elasticsearch
from models.events import BasePipelineEvent

from .concepts_extractor import GraphConceptsExtractor

logger = structlog.get_logger(__name__)


class GraphWorkConceptsExtractor(GraphConceptsExtractor):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
        concept_ids: Iterable[str],
    ):
        super().__init__(event, es_client, neptune_client)
        self.concept_ids = concept_ids

    def extract_concept_ids(self) -> Generator[str]:
        yield from self.concept_ids

    def extract_raw(self) -> Generator[tuple]:
        for concept_ids in self.get_concept_id_batches():
            logger.info("Processing batch of concepts", count=len(concept_ids))
            yield from self.get_concepts(concept_ids).items()
