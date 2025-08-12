from ingestor.models.indexable_concept import ConceptRelatedTo

from .raw_concept import (
    DISPLAY_SOURCE_PRIORITY,
    MissingLabelError,
    get_most_specific_concept_type,
    get_priority_label,
)


def transform_related_concepts(
    related_items: list[dict],
) -> list[ConceptRelatedTo]:
    """
    Process each related concept, extracting its highest-priority label and the relationship type.
    """
    processed_items = []

    for related_item in related_items:
        node, edge = related_item["concept_node"], related_item.get("edge")
        source_nodes = related_item["source_concept_nodes"]

        try:
            label, _ = get_priority_label(node, source_nodes, DISPLAY_SOURCE_PRIORITY)
        except MissingLabelError:
            # If a related concept does not have a label, do not include it
            continue

        relationship_type = ""
        if edge is not None:
            relationship_type = edge["~properties"].get("relationship_type", "")

        concept_types = related_item.get("concept_types", ["Concept"])
        concept_type = get_most_specific_concept_type(concept_types)

        processed_items.append(
            ConceptRelatedTo(
                id=node["~properties"]["id"],
                label=label,
                relationshipType=relationship_type,
                conceptType=concept_type,
            )
        )

    return processed_items


class RawNeptuneRelatedConcepts:
    def __init__(self, related_concepts: dict):
        self.raw_related_concepts = related_concepts

    @property
    def fields_of_work(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("fields_of_work", [])
        return transform_related_concepts(raw_related)

    @property
    def people(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("people", [])
        return transform_related_concepts(raw_related)

    @property
    def narrower_than(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("narrower_than", [])
        return transform_related_concepts(raw_related)

    @property
    def broader_than(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("broader_than", [])
        return transform_related_concepts(raw_related)

    @property
    def frequent_collaborators(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("frequent_collaborators", [])
        return transform_related_concepts(raw_related)

    @property
    def related_topics(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("related_topics", [])
        return transform_related_concepts(raw_related)

    @property
    def related_to(self) -> list[ConceptRelatedTo]:
        raw_related = self.raw_related_concepts.get("related_to", [])
        return transform_related_concepts(raw_related)
