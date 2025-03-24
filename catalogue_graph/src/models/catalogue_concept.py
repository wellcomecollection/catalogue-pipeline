from dataclasses import field
from typing import Any, Optional

from pydantic import BaseModel


def get_priority_source_concept_value(values: dict) -> Any:
    # Sources sorted by priority
    for source in ["nlm-mesh", "lc-subjects", "lc-names", "wikidata", "label-derived"]:
        if (value := values.get(source)) is not None:
            return value


class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str


class CatalogueConceptLink(BaseModel):
    label: str
    id: str


class CatalogueConceptRelatedTo(CatalogueConceptLink):  
    relationshipType: str | None


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
    sameAs: list[str]

    @classmethod
    def from_neptune_result(cls, data: dict, related_to: list[dict], fields_of_work: list[dict], narrower_than: list[dict]) -> "CatalogueConcept":
        labels = {}
        descriptions = {}
        identifiers = []
        alternative_labels = []

        labels["label-derived"] = data["concept"]["~properties"].get("label", "")

        # For now, only extract labels from source concepts which are explicitly linked
        # to the concept via HAS_SOURCE_CONCEPT edges
        for source_concept in data["linked_source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"]

            labels[source] = properties.get("label")
            identifiers.append(
                CatalogueConceptIdentifier(
                    value=properties["id"],
                    identifierType=source,
                )
            )

        # Extract descriptions from _all_ source concepts (utilising both HAS_SOURCE_CONCEPT and SAME_AS edges)
        for source_concept in data["source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"]
            descriptions[source] = properties.get("description")

            for label in properties.get("alternative_labels", "").split("||"):
                if len(label) > 0:
                    alternative_labels.append(label)            
            
        return CatalogueConcept(
            id=data["concept"]["~properties"]["id"],
            type=data["concept"]["~properties"]["type"],
            label=get_priority_source_concept_value(labels),
            alternativeLabels=alternative_labels,
            description=get_priority_source_concept_value(descriptions),
            identifiers=identifiers,
            relatedTo=[CatalogueConceptRelatedTo(
                id=item['node']['~properties']['id'],
                label=item['node']['~properties']['label'],
                relationshipType=item['relationship_type']
            ) for item in related_to],
            fieldsOfWork=[CatalogueConceptLink(
                id=item['~properties']['id'],
                label=item['~properties']['label']
            ) for item in fields_of_work],
            narrowerThan=[CatalogueConceptLink(
                id=item['~properties']['id'],
                label=item['~properties']['label']
            ) for item in narrower_than],
            sameAs=data["same_as_concept_ids"]
        )
