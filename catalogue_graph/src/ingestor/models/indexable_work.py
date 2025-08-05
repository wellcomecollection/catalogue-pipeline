from typing import Optional, Self

from pydantic import BaseModel, Field
from utils.types import ConceptType

from .indexable import DisplayId, DisplayIdentifier, DisplayIdLabel


class DisplayAvailability(DisplayIdLabel):
    type: str = "Availability"


class DisplayLicense(DisplayIdLabel):
    url: str
    type: str = "License"


class DisplayConcept(BaseModel):
    id: Optional[str] = None
    label: str
    identifiers: Optional[list[DisplayIdentifier]] = None
    type: ConceptType = "Concept"


class DisplayContributionRole(BaseModel):
    label: str
    type: str = "ContributionRole"


class DisplayRelation(BaseModel):
    id: Optional[str]
    title: Optional[str]
    referenceNumber: Optional[str] = None
    partOf: Optional[list[Self]] = None
    totalParts: int
    # totalDescendentParts: int # TODO: Is this field needed?
    type: str = "Work"


class DisplayNote(BaseModel):
    contents: list[str]
    noteType: DisplayIdLabel
    type: str = "Note"


class DisplayProductionEvent(BaseModel):
    label: str
    places: list[DisplayConcept]
    agents: list[DisplayConcept]
    dates: list[DisplayConcept]
    function: Optional[DisplayConcept]
    type: str = "ProductionEvent"


class DisplayContributor(BaseModel):
    agent: DisplayConcept
    roles: list[DisplayContributionRole]
    primary: bool
    type: str = "Contributor"


class DisplaySubject(DisplayConcept):
    concepts: list[DisplayConcept]
    type: str = "Subject"


class DisplayGenre(BaseModel):
    label: str
    concepts: list[DisplayConcept]
    type: str = "Genre"


class DisplayAccessCondition(BaseModel):
    method: DisplayIdLabel
    status: Optional[DisplayIdLabel]
    terms: Optional[str]
    note: Optional[str]
    type: str = "AccessCondition"


class DisplayLocation(BaseModel):
    locationType: DisplayIdLabel
    license: Optional[DisplayLicense] = None
    accessConditions: list[DisplayAccessCondition] = None


class DisplayDigitalLocation(DisplayLocation):
    url: str
    credit: Optional[str] = None
    linkText: Optional[str] = None
    type: str = "DigitalLocation"


class DisplayPhysicalLocation(DisplayLocation):
    label: str
    shelfmark: Optional[str] = None
    type: str = "PhysicalLocation"


class DisplayHoldings(BaseModel):
    note: Optional[str]
    enumeration: list[str]
    location: Optional[DisplayPhysicalLocation | DisplayDigitalLocation]
    type: str = "Holdings"


class DisplayItem(BaseModel):
    id: Optional[str]
    identifiers: list[DisplayIdentifier]
    title: Optional[str] = None
    note: Optional[str] = None
    locations: list[DisplayPhysicalLocation | DisplayDigitalLocation] = []
    type: str = "Item"


class WorkDisplay(BaseModel):
    id: str
    title: Optional[str]
    alternativeTitles: list[str]
    referenceNumber: Optional[str]
    description: Optional[str]
    physicalDescription: Optional[str]
    workType: Optional[DisplayIdLabel]
    lettering: Optional[str]
    createdDate: Optional[DisplayConcept]
    contributors: list[DisplayContributor]
    identifiers: list[DisplayIdentifier]
    subjects: list[DisplaySubject]
    genres: list[DisplayGenre]
    thumbnail: Optional[DisplayDigitalLocation]
    items: list[DisplayItem]
    holdings: list[DisplayHoldings]
    availabilities: list[DisplayAvailability]
    production: list[DisplayProductionEvent]
    languages: list[DisplayIdLabel]
    edition: Optional[str]
    notes: list[DisplayNote]
    duration: Optional[int]
    currentFrequency: Optional[str]
    formerFrequency: list[str]
    designation: list[str]
    images: list[DisplayId]
    parts: list[DisplayRelation]
    partOf: list[DisplayRelation]
    type: str = "Work"


class CollectionPath(BaseModel):
    label: Optional[str]
    path: Optional[str]


class WorkQuery(BaseModel):
    id: str
    title: Optional[str]
    referenceNumber: Optional[str]
    physicalDescription: Optional[str]
    lettering: Optional[str]
    edition: Optional[str]
    description: Optional[str]
    alternativeTitles: list[str]
    languageLabels: list[str] = Field(alias="languages.label")
    itemIds: list[str] = Field(alias="items.id")
    contributorsAgentLabel: list[str] = Field(alias="contributors.agent.label")
    genresConceptsLabel: list[str] = Field(alias="genres.concepts.label")
    sourceIdentifierValue: str = Field(alias="sourceIdentifier.value")
    identifiersValue: list[str] = Field(alias="identifiers.value")
    imagesId: list[str] = Field(alias="images.id")
    imagesIdentifiersValue: list[str] = Field(alias="images.identifiers.value")
    itemsIdentifiersValue: list[str] = Field(alias="items.identifiers.value")
    itemsShelfmarksValue: list[str] = Field(alias="items.shelfmark.value")
    notesContents: list[str] = Field(alias="notes.contents")
    partOfTitle: list[str] = Field(alias="partOf.title")
    productionLabel: list[str] = Field(alias="production.label")
    subjectsConceptsLabel: list[str] = Field(alias="subjects.concepts.label")


class IndexableWork(BaseModel):
    query: WorkQuery
    display: WorkDisplay
