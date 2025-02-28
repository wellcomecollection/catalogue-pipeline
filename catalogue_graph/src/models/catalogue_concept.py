from pydantic import BaseModel
from dataclasses import field

class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str

class CatalogueConcept(BaseModel):
    id: str
    identifiers: list[CatalogueConceptIdentifier] = field(default_factory=list)
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    type: str

    @classmethod
    def from_neptune_result(cls, data: dict):
        alternative_labels = []
        identifiers = [CatalogueConceptIdentifier(
            value=target["~properties"]["id"],
            identifierType=target["~properties"]["source"]
        ) for target in data["targets"]]

        if "~properties" in data and "alternative_labels" in data["~properties"]:
            alternative_labels.extend(data["~properties"]["alternative_labels"].split("||"))
        return CatalogueConcept(
            id=data["source"]["~properties"]["id"],
            label=data["source"]["~properties"]["label"],
            type=data["source"]["~properties"]["type"],
            alternativeLabels=alternative_labels,
            identifiers = identifiers
        )
