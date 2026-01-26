from typing import TextIO

import structlog

from ingestor.extractors.base_extractor import ConceptRelatedQuery
from ingestor.extractors.concepts_extractor import GraphConceptsExtractor
from ingestor.models.indexable_concept import (
    ConceptDisplay,
    ConceptQuery,
    ConceptRelatedTo,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.models.neptune.query_result import ExtractedConcept
from ingestor.transformers.concept_override import ConceptTextOverrideProvider
from ingestor.transformers.raw_concept import RawNeptuneConcept
from ingestor.transformers.raw_related_concepts import RawNeptuneRelatedConcepts
from models.events import BasePipelineEvent
from utils.elasticsearch import ElasticsearchMode

from .base_transformer import ElasticsearchBaseTransformer
from .raw_concept import MissingLabelError
from .raw_related_concepts import RawNeptuneRelatedConcept

logger = structlog.get_logger(__name__)


class ElasticsearchConceptsTransformer(ElasticsearchBaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_mode: ElasticsearchMode,
        overrides: TextIO | None = None,
    ) -> None:
        self.source = GraphConceptsExtractor(event, es_mode)
        self.override_provider = ConceptTextOverrideProvider(overrides)

    def _transform_related_concept(
        self, related_concept: RawNeptuneRelatedConcept
    ) -> ConceptRelatedTo | None:
        try:
            return ConceptRelatedTo(
                id=related_concept.wellcome_id,
                relationshipType=related_concept.relationship_type,
                conceptType=related_concept.concept_type,
                label=self.override_provider.display_label_of(related_concept),
            )
        except MissingLabelError:
            # If a related concept does not have a label, do not include it
            return None

    def _transform_related_concepts(
        self, raw_related_concepts: list[RawNeptuneRelatedConcept]
    ) -> list[ConceptRelatedTo]:
        return [
            concept
            for concept in (
                self._transform_related_concept(related_concept)
                for related_concept in raw_related_concepts
            )
            if concept is not None
        ]

    def _get_query(self, neptune_concept: RawNeptuneConcept) -> ConceptQuery:
        return ConceptQuery(
            id=neptune_concept.wellcome_id,
            identifiers=neptune_concept.identifiers,
            label=neptune_concept.label,
            alternativeLabels=neptune_concept.alternative_labels,
            type=neptune_concept.concept_type,
        )

    def _get_display(
        self,
        neptune_concept: RawNeptuneConcept,
        neptune_related: RawNeptuneRelatedConcepts,
    ) -> ConceptDisplay:
        return ConceptDisplay(
            id=neptune_concept.wellcome_id,
            identifiers=neptune_concept.display_identifiers,
            label=neptune_concept.label,
            displayLabel=self.override_provider.display_label_of(neptune_concept),
            alternativeLabels=neptune_concept.alternative_labels,
            type=neptune_concept.concept_type,
            description=self.override_provider.description_of(neptune_concept),
            sameAs=neptune_concept.same_as,
            displayImages=self.override_provider.display_images(neptune_concept),
            relatedConcepts=RelatedConcepts(
                relatedTo=self._transform_related_concepts(neptune_related.related_to),
                fieldsOfWork=self._transform_related_concepts(
                    neptune_related.fields_of_work
                ),
                narrowerThan=self._transform_related_concepts(
                    neptune_related.narrower_than
                ),
                broaderThan=self._transform_related_concepts(
                    neptune_related.broader_than
                ),
                people=self._transform_related_concepts(neptune_related.people),
                frequentCollaborators=self._transform_related_concepts(
                    neptune_related.frequent_collaborators
                ),
                relatedTopics=self._transform_related_concepts(
                    neptune_related.related_topics
                ),
                foundedBy=self._transform_related_concepts(neptune_related.founded_by),
            ),
        )

    def transform_document(
        self, raw_item: tuple[ExtractedConcept, dict[ConceptRelatedQuery, list]]
    ) -> IndexableConcept | None:
        neptune_concept = RawNeptuneConcept(raw_item[0])
        neptune_related = RawNeptuneRelatedConcepts(raw_item[1])

        try:
            query = self._get_query(neptune_concept)
            display = self._get_display(neptune_concept, neptune_related)
            return IndexableConcept(query=query, display=display)
        except MissingLabelError:
            # There is currently one concept which does not have a label ('k6p2u5fh')
            logger.warning(
                "Concept does not have a label and will not be indexed",
                concept_id=neptune_concept.wellcome_id,
            )

        return None
