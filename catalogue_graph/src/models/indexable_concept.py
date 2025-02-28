from pydantic import BaseModel
from dataclasses import field

from models.catalogue_concept import CatalogueConcept


# Query

class ConceptQueryIdentifier(BaseModel):
    value: str
    identifierType: str

class ConceptQuery(BaseModel):
    id: str
    identifiers: list[ConceptQueryIdentifier]
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    type: str

# Display

class ConceptDisplayIdentifier(BaseModel):
    id: str
    label: str
    type: str = "IdentifierType"

class ConceptDisplayIdentifier(BaseModel):
    value: str
    identifierType: str
    type: str = "Identifier"

class ConceptDisplay(BaseModel):
    id: str
    identifiers: list[ConceptDisplayIdentifier]
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    type: str

# Indexable concept - for indexing in elasticsearch

class IndexableConcept(BaseModel):
    query: ConceptQuery
    display: ConceptDisplay

    # static method to create this model from a catalogue concept
    @classmethod
    def from_concept(cls, concept: CatalogueConcept):
        return IndexableConcept(
            query=ConceptQuery(
                id=concept.id,
                identifiers=[ConceptQueryIdentifier(
                    value=identifier.value,
                    identifierType=identifier.identifierType
                ) for identifier in concept.identifiers],
                label=concept.label,
                alternativeLabels=concept.alternativeLabels,
                type=concept.type
            ),
            display=ConceptDisplay(
                id=concept.id,
                identifiers=[ConceptDisplayIdentifier(
                    value=identifier.value,
                    identifierType=identifier.identifierType
                ) for identifier in concept.identifiers],
                label=concept.label,
                alternativeLabels=concept.alternativeLabels,
                type=concept.type
            )
        )
