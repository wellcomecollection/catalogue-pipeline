from ingestor.extractors.base_extractor import ConceptRelatedQuery
from ingestor.models.neptune.query_result import ExtractedRelatedConcept

from .raw_concept import RawNeptuneConcept


class RawNeptuneRelatedConcept(RawNeptuneConcept):
    def __init__(self, extracted_related_concept: ExtractedRelatedConcept):
        super().__init__(extracted_related_concept.target)
        self.relationship_type = extracted_related_concept.relationship_type


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
