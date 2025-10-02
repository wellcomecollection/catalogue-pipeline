from ingestor.extractors.base_extractor import ConceptRelatedQuery
from ingestor.models.neptune.query_result import ExtractedRelatedConcept

from .raw_concept import (
    DISPLAY_SOURCE_PRIORITY,
    get_most_specific_concept_type,
    get_priority_label,
)


class RawNeptuneRelatedConcept:
    def __init__(self, neptune_related_concept: ExtractedRelatedConcept):
        self.raw_related_concept = neptune_related_concept.target
        self.relationship_type = neptune_related_concept.relationship_type

    @property
    def display_label(self) -> str:
        label, _ = get_priority_label(self.raw_related_concept, DISPLAY_SOURCE_PRIORITY)
        return label

    @property
    def wellcome_id(self) -> str:
        return self.raw_related_concept.concept.properties.id

    @property
    def concept_type(self) -> str:
        concept_types = self.raw_related_concept.types
        return get_most_specific_concept_type(concept_types)


class RawNeptuneRelatedConcepts:
    def __init__(self, related_concepts: dict[ConceptRelatedQuery, list]):
        self.raw_related_concepts = related_concepts

    @property
    def fields_of_work(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("fields_of_work", [])
        ]

    @property
    def people(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("people", [])
        ]

    @property
    def narrower_than(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("narrower_than", [])
        ]

    @property
    def broader_than(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("broader_than", [])
        ]

    @property
    def frequent_collaborators(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("frequent_collaborators", [])
        ]

    @property
    def related_topics(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("related_topics", [])
        ]

    @property
    def related_to(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("related_to", [])
        ]

    @property
    def founded_by(self) -> list[RawNeptuneRelatedConcept]:
        return [
            RawNeptuneRelatedConcept(concept)
            for concept in self.raw_related_concepts.get("founded_by", [])
        ]
