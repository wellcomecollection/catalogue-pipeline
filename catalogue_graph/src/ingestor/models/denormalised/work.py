from datetime import datetime
from typing import Literal

from pydantic import BaseModel

type WorkType = Literal["Standard", "Collection", "Series", "Section"]


class Id(BaseModel):
    id: str


class Label(BaseModel):
    label: str


class IdLabel(BaseModel):
    id: str
    label: str


class CollectionPath(BaseModel):
    path: str
    label: str | None = None


class Type(BaseModel):
    type: str


class AccessCondition(BaseModel):
    method: Type
    status: Type | None = None
    terms: str | None = None
    note: str | None = None


class Location(BaseModel):
    locationType: Id
    license: Id | None = None
    accessConditions: list[AccessCondition]


class DigitalLocation(Location):
    url: str
    credit: str | None = None
    linkText: str | None = None


class PhysicalLocation(Location):
    label: str
    shelfmark: str | None = None


class SourceIdentifier(BaseModel):
    identifierType: Id
    ontologyType: str
    value: str


class Note(BaseModel):
    noteType: IdLabel
    contents: str


class Holdings(BaseModel):
    note: str | None = None
    enumeration: list[str] = []
    location: PhysicalLocation | DigitalLocation | None = None


class AllIdentifiers(BaseModel):
    canonicalId: str
    sourceIdentifier: SourceIdentifier
    otherIdentifiers: list[SourceIdentifier] = []


class Unidentifiable(BaseModel):
    canonicalId: None = None
    type: Literal["Unidentifiable"]


class ImageData(BaseModel):
    id: AllIdentifiers
    version: int
    locations: list[DigitalLocation]


class Item(BaseModel):
    id: AllIdentifiers | Unidentifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []


class Concept(BaseModel):
    id: AllIdentifiers | Unidentifiable | None
    label: str
    type: str = "Concept"


class Contributor(BaseModel):
    agent: Concept
    roles: list[Label] = []
    primary: bool = True


class Subject(Concept):
    concepts: list[Concept]


class Genre(BaseModel):
    label: str
    concepts: list[Concept]


class ProductionEvent(BaseModel):
    label: str
    places: list[Concept]
    agents: list[Concept]
    dates: list[Concept]
    function: Concept | None = None


class DenormalisedWorkData(BaseModel):
    title: str | None = None
    otherIdentifiers: list[SourceIdentifier]
    alternativeTitles: list[str] = []
    format: IdLabel | None = None
    description: str | None = None
    physicalDescription: str | None = None
    lettering: str | None = None
    createdDate: Concept | None = None
    subjects: list[Subject] = []
    genres: list[Genre] = []
    contributors: list[Contributor] = []
    thumbnail: DigitalLocation | None = None
    production: list[ProductionEvent] = []
    languages: list[IdLabel] = []
    edition: str | None = None
    notes: list[Note] = []
    duration: int | None = None
    items: list[Item] = []
    holdings: list[Holdings] = []
    collectionPath: CollectionPath | None = None
    referenceNumber: str | None = None
    imageData: list[ImageData] = []
    workType: WorkType = "Standard"
    currentFrequency: str | None = None
    formerFrequency: list[str] = []
    designation: list[str] = []


class DenormalisedWorkState(BaseModel):
    sourceIdentifier: SourceIdentifier
    canonicalId: str
    mergedTime: datetime
    sourceModifiedTime: datetime
    availabilities: list[Id]


class DenormalisedWork(BaseModel):
    data: DenormalisedWorkData
    state: DenormalisedWorkState
