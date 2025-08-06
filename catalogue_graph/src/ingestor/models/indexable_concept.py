from pydantic import BaseModel

from ingestor.models.display.identifier import DisplayIdentifier
from utils.types import ConceptType


class ConceptDescription(BaseModel):
    text: str
    sourceLabel: str
    sourceUrl: str


class ConceptRelatedTo(BaseModel):
    label: str
    id: str
    relationshipType: str | None
    conceptType: str


class RelatedConcepts(BaseModel):
    relatedTo: list[ConceptRelatedTo]
    fieldsOfWork: list[ConceptRelatedTo]
    narrowerThan: list[ConceptRelatedTo]
    broaderThan: list[ConceptRelatedTo]
    people: list[ConceptRelatedTo]
    frequentCollaborators: list[ConceptRelatedTo]
    relatedTopics: list[ConceptRelatedTo]


class ConceptIdentifier(BaseModel):
    value: str
    identifierType: str


class ConceptQuery(BaseModel):
    id: str
    identifiers: list[ConceptIdentifier]
    label: str
    alternativeLabels: list[str]
    type: ConceptType


class ConceptDisplay(BaseModel):
    id: str
    identifiers: list[DisplayIdentifier]
    label: str
    displayLabel: str
    alternativeLabels: list[str]
    description: ConceptDescription | None
    type: ConceptType
    relatedConcepts: RelatedConcepts
    sameAs: list[str]


class IndexableConcept(BaseModel):
    query: ConceptQuery
    display: ConceptDisplay
