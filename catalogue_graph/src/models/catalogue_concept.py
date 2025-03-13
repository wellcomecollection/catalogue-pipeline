from dataclasses import field
from typing import Any, Optional

from pydantic import BaseModel


def get_priority_source_concept_value(values: dict) -> Any:
    # Sources sorted by priority
    for source in ["nlm-mesh", "lc-names", "lc-subjects", "wikidata", "label-derived"]:
        if (value := values.get(source)) is not None:
            return value


class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str


class CatalogueConcept(BaseModel):
    id: str
    identifiers: list[CatalogueConceptIdentifier] = field(default_factory=list)
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    description: Optional[str]
    type: str

    @classmethod
    def from_neptune_result(cls, data: dict) -> "CatalogueConcept":
        labels = {}
        descriptions = {}
        identifiers = []
        alternative_labels = []

        labels["label-derived"] = data["concept"]["~properties"].get("label", "")

        # For now, only extract labels and alternative labels only from source concepts which are explicitly linked
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

            for label in properties.get("alternative_labels", "").split("||"):
                if len(label) > 0:
                    alternative_labels.append(label)

        # Extract descriptions from _all_ source concepts (utilising both HAS_SOURCE_CONCEPT and SAME_AS edges)
        for source_concept in data["source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"]
            descriptions[source] = properties.get("description")
                
        return CatalogueConcept(
            id=data["concept"]["~properties"]["id"],
            type=data["concept"]["~properties"]["type"],
            label=get_priority_source_concept_value(labels),
            alternativeLabels=alternative_labels,
            description=get_priority_source_concept_value(descriptions),
            identifiers=identifiers,
        )
