from models.indexable_concept import (
    ConceptDisplay,
    ConceptQuery,
    IndexableConcept,
    RelatedConcepts,
)

from .raw_neptune_concept import RawNeptuneConcept
from .raw_neptune_related_concepts import RawNeptuneRelatedConcepts


class ElasticsearchConceptsTransformer:
    def transform_document(
            self, concept: dict, related_concepts: dict
    ) -> IndexableConcept:
        neptune_concept = RawNeptuneConcept(concept, related_concepts)
        neptune_related = RawNeptuneRelatedConcepts(
            neptune_concept.wellcome_id, related_concepts
        )

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
            alternativeLabels=neptune_concept.alternative_labels,
            type=neptune_concept.concept_type,
            description=neptune_concept.description,
            sameAs=neptune_concept.same_as,
            relatedConcepts=RelatedConcepts(
                relatedTo=neptune_related.related_to,
                fieldsOfWork=neptune_related.fields_of_work,
                narrowerThan=neptune_related.narrower_than,
                broaderThan=neptune_related.broader_than,
                people=neptune_related.people,
                referencedTogether=neptune_related.referenced_together,
                frequentCollaborators=neptune_related.frequent_collaborators,
                relatedTopics=neptune_related.related_topics,
            ),
        )

        return IndexableConcept(query=query, display=display)
