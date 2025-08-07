from collections.abc import Generator
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

type WorkType = Literal["Standard", "Collection", "Series", "Section"]


class FromCamelCaseModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


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


class Location(FromCamelCaseModel):
    location_type: Id
    license: Id | None = None
    access_conditions: list[AccessCondition]


class DigitalLocation(Location):
    url: str
    credit: str | None = None
    link_text: str | None = None


class PhysicalLocation(Location):
    label: str
    shelfmark: str | None = None


class SourceIdentifier(FromCamelCaseModel):
    identifier_type: Id
    ontology_type: str
    value: str


class Note(FromCamelCaseModel):
    note_type: IdLabel
    contents: str


class Holdings(BaseModel):
    note: str | None = None
    enumeration: list[str] = []
    location: PhysicalLocation | DigitalLocation | None = None


class AllIdentifiers(FromCamelCaseModel):
    canonical_id: str
    source_identifier: SourceIdentifier
    other_identifiers: list[SourceIdentifier] = []

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield self.source_identifier
        yield from self.other_identifiers


class Unidentifiable(FromCamelCaseModel):
    canonical_id: None = None
    type: Literal["Unidentifiable"]

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield from []


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


class DenormalisedWorkData(FromCamelCaseModel):
    title: str | None = None
    other_identifiers: list[SourceIdentifier]
    alternative_titles: list[str]
    format: IdLabel | None = None
    description: str | None = None
    physical_description: str | None = None
    lettering: str | None = None
    created_date: Concept | None = None
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
    collection_path: CollectionPath | None = None
    reference_number: str | None = None
    image_data: list[ImageData] = []
    work_type: WorkType = "Standard"
    current_frequency: str | None = None
    former_frequency: list[str] = []
    designation: list[str] = []


class DenormalisedWorkState(FromCamelCaseModel):
    source_identifier: SourceIdentifier
    canonical_id: str
    merged_time: datetime
    source_modified_time: datetime
    availabilities: list[Id]


class DenormalisedWork(BaseModel):
    data: DenormalisedWorkData
    state: DenormalisedWorkState
