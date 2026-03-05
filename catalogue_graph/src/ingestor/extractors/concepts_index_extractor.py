from collections.abc import Generator

import structlog
from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from ingestor.extractors.base_extractor import ConceptRelatedQuery
from ingestor.models.neptune.query_result import (
    ExtractedConcept,
    ExtractedRelatedConcept,
)
from models.events import BasePipelineEvent
from graph.sources.catalogue.concepts_source import (
    CatalogueConceptsSource,
)

from .base_concepts_extractor import GraphBaseConceptsExtractor

logger = structlog.get_logger(__name__)


ExtractedRelatedConcepts = dict[ConceptRelatedQuery, list[ExtractedRelatedConcept]]


class ConceptsIndexExtractor(GraphBaseConceptsExtractor):
    """
    Extracts concepts and their related concepts to produce standalone documents for the concepts index.
    """

    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(neptune_client)
        self.es_source = CatalogueConceptsSource(
            event,
            es_client=es_client,
        )

    def get_concept_ids_to_process(self) -> Generator[str]:
        """Stream works from the merged index and yield all concept IDs referenced from each work"""
        for extracted in self.es_source.stream_raw():
            yield extracted.concept.id.canonical_id

    def extract_raw(
        self,
    ) -> Generator[tuple[ExtractedConcept, ExtractedRelatedConcepts]]:
        for concept_ids in self.get_consistent_batches():
            logger.info("Processing batch of concepts", count=len(concept_ids))
            concepts = self.get_concepts(concept_ids).items()
            all_related_concepts = self.get_related_concepts(concept_ids)

            for concept_id, concept in concepts:
                primary_id = self.get_primary(concept_id)
                related_concepts = {}

                # 'key' corresponds to relationship type, such as broader_than or frequent_collaborators
                for key in all_related_concepts:
                    if primary_id in all_related_concepts[key]:
                        related_concepts[key] = all_related_concepts[key][primary_id]

                yield concept, related_concepts
