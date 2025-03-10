from dataclasses import field

from pydantic import BaseModel


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
    def from_neptune_result(cls, data: dict) -> "CatalogueConcept":
        identifiers = [
            CatalogueConceptIdentifier(
                value=target["~properties"]["id"],
                identifierType=target["~properties"]["source"],
            )
            for target in data["targets"]
        ]

        alternative_labels = [
            label
            for target in data["targets"]
            if "~properties" in target and "alternative_labels" in target["~properties"]
            for label in target["~properties"]["alternative_labels"].split("||")
        ]

        return CatalogueConcept(
            id=data["source"]["~properties"]["id"],
            label=data["source"]["~properties"]["label"],
            type=data["source"]["~properties"]["type"],
            alternativeLabels=alternative_labels,
            identifiers=identifiers,
        )
