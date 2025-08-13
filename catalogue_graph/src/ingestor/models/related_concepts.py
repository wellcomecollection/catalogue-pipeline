from .concept import (
    DISPLAY_SOURCE_PRIORITY,
    get_most_specific_concept_type,
    get_priority_label,
)


class RawNeptuneRelatedConcept:
    def __init__(self, neptune_related_concept: dict):
        self.raw_related_concept = neptune_related_concept
        self.node = self.raw_related_concept["concept_node"]
        self.edge = self.raw_related_concept.get("edge")
        self.source_nodes = self.raw_related_concept["source_concept_nodes"]

    @property
    def display_label(self) -> str:
        label, _ = get_priority_label(self.node, self.source_nodes, DISPLAY_SOURCE_PRIORITY)
        return label

    @property
    def wellcome_id(self) -> str:
        return self.node["~properties"]["id"]

    @property
    def relationship_type(self) -> str | None:
        return "" if self.edge is None else self.edge["~properties"].get("relationship_type", "")

    @property
    def concept_type(self) -> str:
        concept_types = self.raw_related_concept.get("concept_types", ["Concept"])
        return get_most_specific_concept_type(concept_types)


class RawNeptuneRelatedConcepts:
    def __init__(self, concept_id: str, all_related_concepts: dict):
        self.concept_id = concept_id
        self.raw_related_concepts = all_related_concepts

    def _get_related_concepts(self, key: str) -> list[RawNeptuneRelatedConcept]:
        related_concepts: list = self.raw_related_concepts[key].get(self.concept_id, [])
        return related_concepts

    @property
    def fields_of_work(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in self._get_related_concepts("fields_of_work")]

    @property
    def people(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("people"))]

    @property
    def narrower_than(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("narrower_than"))]

    @property
    def broader_than(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("broader_than"))]

    @property
    def frequent_collaborators(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("frequent_collaborators"))]

    @property
    def related_topics(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("related_topics"))]

    @property
    def related_to(self) -> list[RawNeptuneRelatedConcept]:
        return [RawNeptuneRelatedConcept(concept) for concept in (self._get_related_concepts("related_to"))]
