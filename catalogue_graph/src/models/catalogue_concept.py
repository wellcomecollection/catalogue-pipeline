from dataclasses import field
from typing import Any, Optional

from pydantic import BaseModel


class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str


class CatalogueConceptLink(BaseModel):
    label: str
    id: str


class CatalogueConceptRelatedTo(CatalogueConceptLink):
    relationshipType: str | None


def get_priority_source_concept_value(concept_node: dict | None, source_concept_nodes: list[dict], key: str) -> Any:
    values = {}

    if concept_node is not None:
        values["label-derived"] = concept_node["~properties"].get(key, "")

    for source_concept in source_concept_nodes:
        properties = source_concept["~properties"]
        source = properties["source"]
        values[source] = properties.get(key)

    # Sources sorted by priority
    for source in ["nlm-mesh", "lc-subjects", "lc-names", "wikidata", "label-derived"]:
        if (value := values.get(source)) is not None:
            return value


def get_concept_link(related_item: dict) -> CatalogueConceptLink:
    return CatalogueConceptLink(
        id=related_item['concept_node']['~properties']['id'],
        label=get_priority_source_concept_value(related_item['concept_node'], related_item['source_concept_nodes'], 'label'),
    )


class CatalogueConcept(BaseModel):
    id: str
    identifiers: list[CatalogueConceptIdentifier] = field(default_factory=list)
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    description: Optional[str]
    type: str
    relatedTo: list[CatalogueConceptRelatedTo]
    fieldsOfWork: list[CatalogueConceptLink]
    narrowerThan: list[CatalogueConceptLink]
    broaderThan: list[CatalogueConceptLink]
    people: list[CatalogueConceptLink]
    sameAs: list[str]

    @classmethod
    def from_neptune_result(cls, data: dict, related_to: list[dict], fields_of_work: list[dict], narrower_than: list[dict], broader_than: list[dict], people: list[dict]) -> "CatalogueConcept":
        identifiers = []
        alternative_labels = []

        # For now, only extract labels from source concepts which are explicitly linked
        # to the concept via HAS_SOURCE_CONCEPT edges        
        label = get_priority_source_concept_value(data["concept"], data["linked_source_concepts"], "label")
        description = get_priority_source_concept_value(None, data["source_concepts"], "description")

        for source_concept in data["linked_source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"]

            identifiers.append(
                CatalogueConceptIdentifier(
                    value=properties["id"],
                    identifierType=source,
                )
            )

        # Extract descriptions from _all_ source concepts (utilising both HAS_SOURCE_CONCEPT and SAME_AS edges)
        for source_concept in data["source_concepts"]:
            for alternative_label in source_concept["~properties"].get("alternative_labels", "").split("||"):
                if len(alternative_label) > 0:
                    alternative_labels.append(alternative_label)
        
        return CatalogueConcept(
            id=data["concept"]["~properties"]["id"],
            type=data["concept"]["~properties"]["type"],
            label=label,
            alternativeLabels=alternative_labels,
            description=description,
            identifiers=identifiers,
            relatedTo=[CatalogueConceptRelatedTo(
                id=get_concept_link(item).id,
                label=get_concept_link(item).label,
                relationshipType=item['edge']['~properties'].get('relationship_type')
            ) for item in related_to],
            fieldsOfWork=[get_concept_link(item) for item in fields_of_work],
            narrowerThan=[get_concept_link(item) for item in narrower_than],
            broaderThan=[get_concept_link(item) for item in broader_than],
            people=[get_concept_link(item) for item in people],
            sameAs=data["same_as_concept_ids"]
        )
