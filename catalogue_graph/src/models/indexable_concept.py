from dataclasses import field
from typing import Optional

from pydantic import BaseModel

from models.catalogue_concept import (
    CatalogueConcept,
    RelatedConcepts,
)
from models.graph_node import ConceptType

# Query


class ConceptQueryIdentifier(BaseModel):
    value: str
    identifierType: str


class ConceptQuery(BaseModel):
    id: str
    identifiers: list[ConceptQueryIdentifier]
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    type: ConceptType


# Display


class ConceptDisplayIdentifierType(BaseModel):
    id: str
    label: str
    type: str = "IdentifierType"

    @classmethod
    def from_source_type(cls, source_type: str) -> "ConceptDisplayIdentifierType":
        if source_type == "label-derived":
            label = "Identifier derived from the label of the referent"
        elif source_type == "nlm-mesh":
            label = "Medical Subject Headings (MeSH) identifier"
        elif source_type == "lc-names":
            label = "Library of Congress Name authority records"
        elif source_type == "lc-subjects":
            label = "Library of Congress Subject Headings (LCSH)"
        elif source_type == "viaf":
            label = "VIAF: The Virtual International Authority File"
        elif source_type == "fihrist":
            label = "Fihrist Authority"
        else:
            raise ValueError(f"Unknown source concept type: {source_type}.")

        return ConceptDisplayIdentifierType(id=source_type, label=label)


class ConceptDisplayIdentifier(BaseModel):
    value: str
    type: str = "Identifier"
    identifierType: ConceptDisplayIdentifierType


class ConceptDisplay(BaseModel):
    id: str
    identifiers: list[ConceptDisplayIdentifier]
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    description: Optional[str]
    type: ConceptType
    relatedConcepts: RelatedConcepts
    sameAs: list[str]


# Indexable concept - for indexing in elasticsearch
class IndexableConcept(BaseModel):
    query: ConceptQuery
    display: ConceptDisplay

    # static method to create this model from a catalogue concept
    @classmethod
    def from_concept(cls, concept: CatalogueConcept) -> "IndexableConcept":
        return IndexableConcept(
            query=ConceptQuery(
                id=concept.id,
                identifiers=[
                    ConceptQueryIdentifier(
                        value=identifier.value, identifierType=identifier.identifierType
                    )
                    for identifier in concept.identifiers
                ],
                label=concept.label,
                alternativeLabels=concept.alternativeLabels,
                type=concept.type,
            ),
            display=ConceptDisplay(
                id=concept.id,
                identifiers=[
                    ConceptDisplayIdentifier(
                        value=identifier.value,
                        identifierType=ConceptDisplayIdentifierType.from_source_type(
                            identifier.identifierType
                        ),
                    )
                    for identifier in concept.identifiers
                ],
                label=concept.label,
                alternativeLabels=concept.alternativeLabels,
                type=concept.type,
                description=concept.description,
                relatedConcepts=concept.relatedConcepts,
                sameAs=concept.sameAs,
            ),
        )
