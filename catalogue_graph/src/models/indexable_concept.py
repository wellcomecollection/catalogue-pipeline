from typing import Optional

from pydantic import BaseModel

from models.graph_node import ConceptType


class ConceptDescription(BaseModel):
    text: str
    sourceLabel: str
    sourceUrl: str


class CatalogueConceptRelatedTo(BaseModel):
    label: str
    id: str
    relationshipType: str | None
    conceptType: str


class RelatedConcepts(BaseModel):
    relatedTo: list[CatalogueConceptRelatedTo]
    fieldsOfWork: list[CatalogueConceptRelatedTo]
    narrowerThan: list[CatalogueConceptRelatedTo]
    broaderThan: list[CatalogueConceptRelatedTo]
    people: list[CatalogueConceptRelatedTo]
    referencedTogether: list[CatalogueConceptRelatedTo]
    frequentCollaborators: list[CatalogueConceptRelatedTo]
    relatedTopics: list[CatalogueConceptRelatedTo]


class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str

class ConceptQuery(BaseModel):
    id: str
    identifiers: list[CatalogueConceptIdentifier]
    label: str
    alternativeLabels: list[str]
    type: ConceptType


class ConceptDisplay(BaseModel):
    id: str
    identifiers: list[ConceptDisplayIdentifier]
    label: str
    alternativeLabels: list[str]
    description: Optional[ConceptDescription]
    type: ConceptType
    relatedConcepts: RelatedConcepts
    sameAs: list[str]


# Indexable concept - for indexing in elasticsearch
class IndexableConcept(BaseModel):
    query: ConceptQuery
    display: ConceptDisplay
