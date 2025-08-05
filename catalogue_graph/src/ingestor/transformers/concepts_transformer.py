from ingestor.extractors.concepts_extractor import GraphConceptsExtractor
from ingestor.models.indexable_concept import (
    ConceptDisplay,
    ConceptQuery,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.transformers.raw_concept import RawNeptuneConcept
from ingestor.transformers.raw_related_concepts import RawNeptuneRelatedConcepts

from .base_transformer import ElasticsearchBaseTransformer
from .raw_concept import MissingLabelError


class ElasticsearchConceptsTransformer(ElasticsearchBaseTransformer):
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source = GraphConceptsExtractor(start_offset, end_index, is_local)

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
            displayLabel=neptune_concept.display_label,
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
                frequentCollaborators=neptune_related.frequent_collaborators,
                relatedTopics=neptune_related.related_topics,
            ),
        )

    def transform_document(
        self, raw_item: tuple[dict, dict]
    ) -> IndexableConcept | None:
        neptune_concept = RawNeptuneConcept(raw_item[0])
        neptune_related = RawNeptuneRelatedConcepts(raw_item[1])

        try:
            query = self._get_query(neptune_concept)
            display = self._get_display(neptune_concept, neptune_related)
            return IndexableConcept(query=query, display=display)
        except MissingLabelError:
            # There is currently one concept which does not have a label ('k6p2u5fh')
            print(
                f"Concept {neptune_concept.wellcome_id} does not have a label and will not be indexed."
            )

        return None
