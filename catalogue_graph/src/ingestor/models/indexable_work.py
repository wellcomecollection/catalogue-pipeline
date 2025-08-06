from typing import Self

from pydantic import BaseModel, Field

from utils.types import ConceptType

from .indexable import DisplayId, DisplayIdentifier, DisplayIdLabel


class DisplayLicense(DisplayIdLabel):
    url: str
    type: str = "License"


class DisplayConcept(BaseModel):
    id: str | None = None
    label: str
    identifiers: list[DisplayIdentifier] | None = None
    type: ConceptType = "Concept"


class DisplayContributionRole(BaseModel):
    label: str
    type: str = "ContributionRole"


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    partOf: list[Self] | None = None
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
    function: DisplayConcept | None
    type: str = "ProductionEvent"


class DisplayContributor(BaseModel):
    agent: DisplayConcept
    roles: list[DisplayContributionRole]
    primary: bool
    type: str = "Contributor"


class DisplaySubject(DisplayConcept):
    concepts: list[DisplayConcept]
    type: ConceptType = "Subject"


class DisplayGenre(BaseModel):
    label: str
    concepts: list[DisplayConcept]
    type: ConceptType = "Genre"


class DisplayAccessCondition(BaseModel):
    method: DisplayIdLabel
    status: DisplayIdLabel | None
    terms: str | None
    note: str | None
    type: str = "AccessCondition"


class DisplayLocation(BaseModel):
    locationType: DisplayIdLabel
    license: DisplayLicense | None = None
    accessConditions: list[DisplayAccessCondition]


class DisplayDigitalLocation(DisplayLocation):
    url: str
    credit: str | None = None
    linkText: str | None = None
    type: str = "DigitalLocation"


class DisplayPhysicalLocation(DisplayLocation):
    label: str
    shelfmark: str | None = None
    type: str = "PhysicalLocation"


class DisplayHoldings(BaseModel):
    note: str | None
    enumeration: list[str]
    location: DisplayPhysicalLocation | DisplayDigitalLocation | None
    type: str = "Holdings"


class DisplayItem(BaseModel):
    id: str | None
    identifiers: list[DisplayIdentifier]
    title: str | None = None
    note: str | None = None
    locations: list[DisplayPhysicalLocation | DisplayDigitalLocation] = []
    type: str = "Item"


class WorkDisplay(BaseModel):
    id: str
    title: str | None
    alternativeTitles: list[str]
    referenceNumber: str | None
    description: str | None
    physicalDescription: str | None
    workType: DisplayIdLabel | None
    lettering: str | None
    createdDate: DisplayConcept | None
    contributors: list[DisplayContributor]
    identifiers: list[DisplayIdentifier]
    subjects: list[DisplaySubject]
    genres: list[DisplayGenre]
    thumbnail: DisplayDigitalLocation | None
    items: list[DisplayItem]
    holdings: list[DisplayHoldings]
    availabilities: list[DisplayIdLabel]
    production: list[DisplayProductionEvent]
    languages: list[DisplayIdLabel]
    edition: str | None
    notes: list[DisplayNote]
    duration: int | None
    currentFrequency: str | None
    formerFrequency: list[str]
    designation: list[str]
    images: list[DisplayId]
    parts: list[DisplayRelation]
    partOf: list[DisplayRelation]
    type: str = "Work"


class CollectionPath(BaseModel):
    label: str | None
    path: str | None


class WorkQuery(BaseModel):
    id: str
    title: str | None
    referenceNumber: str | None
    physicalDescription: str | None
    lettering: str | None
    edition: str | None
    description: str | None
    alternativeTitles: list[str]
    languagesLabel: list[str] = Field(serialization_alias="languages.label")
    sourceIdentifierValue: str = Field(serialization_alias="sourceIdentifier.value")
    identifiersValue: list[str] = Field(serialization_alias="identifiers.value")
    imagesId: list[str] = Field(serialization_alias="images.id")
    imagesIdentifiersValue: list[str] = Field(
        serialization_alias="images.identifiers.value"
    )
    itemsIdentifiersValue: list[str] = Field(
        serialization_alias="items.identifiers.value"
    )
    itemsId: list[str] = Field(serialization_alias="items.id")
    itemsShelfmarksValue: list[str] = Field(serialization_alias="items.shelfmark.value")
    notesContents: list[str] = Field(serialization_alias="notes.contents")
    partOfTitle: list[str] = Field(serialization_alias="partOf.title")
    productionLabel: list[str] = Field(serialization_alias="production.label")
    subjectsConceptsLabel: list[str] = Field(
        serialization_alias="subjects.concepts.label"
    )
    contributorsAgentLabel: list[str] = Field(
        serialization_alias="contributors.agent.label"
    )
    genresConceptsLabel: list[str] = Field(serialization_alias="genres.concepts.label")
    collectionPathLabel: str | None = Field(serialization_alias="collectionPath.label")
    collectionPathPath: str | None = Field(serialization_alias="collectionPath.path")


class IndexableWork(BaseModel):
    query: WorkQuery
    display: WorkDisplay
