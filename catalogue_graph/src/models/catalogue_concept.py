from dataclasses import field
from typing import Any, Optional

from pydantic import BaseModel


def get_priority_source_concept_value(values: dict) -> Any:
    # Sources sorted by priority
    for source in ["nlm-mesh", "lc-names", "lc-subjects", "wikidata"]:
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
        descriptions = {}
        identifiers = []
        alternative_labels = set()
        for source_concept in data["source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"] 

            descriptions[source] = properties.get("description")
            
            identifiers.append(CatalogueConceptIdentifier(
                value=properties["id"],
                identifierType=source,
            ))

            for label in properties.get("alternative_labels", "").split("||"):
                if len(label) > 0:
                    alternative_labels.add(label)

        return CatalogueConcept(
            id=data["concept"]["~properties"]["id"],
            label=data["concept"]["~properties"].get("label", ""),
            type=data["concept"]["~properties"]["type"],
            alternativeLabels=list(alternative_labels),
            description=get_priority_source_concept_value(descriptions),
            identifiers=identifiers,
        )
