from typing import TextIO

from ingestor.models.concept import MissingLabelError, RawNeptuneConcept
from ingestor.models.indexable_concept import (
    ConceptDisplay,
    ConceptQuery,
    ConceptRelatedTo,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.models.related_concepts import (
    RawNeptuneRelatedConcept,
    RawNeptuneRelatedConcepts,
)
from ingestor.transformers.concept_override import ConceptTextOverrideProvider


class ElasticsearchConceptsTransformer:
    def __init__(self, overrides: TextIO | None = None):
        self.override_provider = ConceptTextOverrideProvider(overrides)

    def _transform_related_concept(self, related_concept: RawNeptuneRelatedConcept):
        try:
            return ConceptRelatedTo(
                id=related_concept.wellcome_id,
                relationshipType=related_concept.relationship_type,
                conceptType=related_concept.concept_type,
                label=self.override_provider.display_label_of(related_concept)
            )
        except MissingLabelError:
            # If a related concept does not have a label, do not include it
            return None

    def _transform_related_concepts(self, raw_related_concepts: list[RawNeptuneRelatedConcept]) -> list[
        ConceptRelatedTo]:
        return [concept for concept in (
            self._transform_related_concept(related_concept)
            for related_concept in raw_related_concepts
        ) if concept is not None]

    def transform_document(
            self,
            neptune_concept: RawNeptuneConcept,
            neptune_related: RawNeptuneRelatedConcepts,
    ) -> IndexableConcept:
        query = ConceptQuery(
            id=neptune_concept.wellcome_id,
            identifiers=neptune_concept.identifiers,
            label=neptune_concept.label,
            alternativeLabels=neptune_concept.alternative_labels,
            type=neptune_concept.concept_type,
        )
        display = ConceptDisplay(
            id=neptune_concept.wellcome_id,
            identifiers=neptune_concept.display_identifiers,
            label=neptune_concept.label,
            displayLabel=self.override_provider.display_label_of(neptune_concept),
            alternativeLabels=neptune_concept.alternative_labels,
            type=neptune_concept.concept_type,
            description=self.override_provider.description_of(neptune_concept),
            sameAs=neptune_concept.same_as,
            relatedConcepts=RelatedConcepts(
                relatedTo=self._transform_related_concepts(neptune_related.related_to),
                fieldsOfWork=self._transform_related_concepts(neptune_related.fields_of_work),
                narrowerThan=self._transform_related_concepts(neptune_related.narrower_than),
                broaderThan=self._transform_related_concepts(neptune_related.broader_than),
                people=self._transform_related_concepts(neptune_related.people),
                frequentCollaborators=self._transform_related_concepts(neptune_related.frequent_collaborators),
                relatedTopics=self._transform_related_concepts(neptune_related.related_topics),
            ),
        )

        return IndexableConcept(query=query, display=display)
