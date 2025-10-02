from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ElasticsearchModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
        serialize_by_alias=True,
    )


class SourceIdentifier(ElasticsearchModel):
    identifier_type: str  # e.g. 'ebsco-alt-lookup'
    ontology_type: str  # always 'Work' for this adapter
    value: str  # raw source ID

    def __str__(self) -> str:
        return f"Work[{self.identifier_type}/{self.value}]"


class BaseWork(BaseModel):
    source_identifier: SourceIdentifier


class DeletedWork(ElasticsearchModel, BaseWork):
    deleted_reason: str


ConceptType = Literal[
    "Person",
    "Concept",
    "Organisation",
    "Place",
    "Agent",
    "Meeting",
    "Genre",
    "Period",
    "Subject",
]


class SourceConcept(BaseModel):
    id: SourceIdentifier | None = None
    label: str
    type: ConceptType = "Concept"


class Subject(SourceConcept):
    concepts: list[SourceConcept] = []
    label: str
    type: ConceptType = "Subject"


class Contributor(BaseModel):
    agent: SourceConcept
    roles: list[str] = []
    primary: bool = True


class Genre(SourceConcept):
    label: str
    concepts: list[SourceConcept] = Field(default_factory=list)


class DateTimeRange(BaseModel):
    from_time: str = Field(alias="from")
    to_time: str = Field(alias="to")
    label: str | None = None


class Period(SourceConcept):
    range: DateTimeRange | None = None


class ProductionEvent(BaseModel):
    label: str
    places: list[SourceConcept]
    agents: list[SourceConcept]
    dates: list[Period]
    function: SourceConcept | None = None


class IdLabel(BaseModel):
    id: str
    label: str


class Language(IdLabel):
    pass


class Format(IdLabel):
    pass


EBooks = Format(id="v", label="E-Books")
EJournals = Format(id="j", label="E-Journals")


class Type(BaseModel):
    type: str


class Id(BaseModel):
    id: str


class AccessMethod(Type):
    pass


ViewOnline = AccessMethod(type="ViewOnline")


class AccessStatusRelationship(Type):
    pass


# TODO: (Resource vs RelatedResource)
#  Scala code says this is not exposed the public API,
#  but we need it for the "available online" filter.
#  I wonder if that is actually true, and we can get
#  rid of the whole thing?
#  All EBSCO records are currently "Resource"
Resource = AccessStatusRelationship(type="Resource")


class AccessStatus(Type):
    relationship: AccessStatusRelationship


LicensedResource = AccessStatus(type="LicensedResources", relationship=Resource)


class AccessCondition(BaseModel):
    method: AccessMethod
    status: AccessStatus | None = None
    terms: str | None = None
    note: str | None = None


class LocationType(Id):
    pass


OnlineResource = LocationType(id="online-resource")


class Location(ElasticsearchModel):
    location_type: LocationType
    license: Id | None = None
    access_conditions: list[AccessCondition]


class DigitalLocation(Location):
    url: str
    credit: str | None = None
    link_text: str | None = None


class PhysicalLocation(Location):
    label: str
    shelfmark: str | None = None


class Holdings(BaseModel):
    note: str | None = None
    enumeration: list[str] = Field(default_factory=list)
    location: PhysicalLocation | DigitalLocation | None = None


class SourceWork(ElasticsearchModel, BaseWork):
    title: str
    alternative_titles: list[str] = Field(default_factory=list)
    other_identifiers: list[SourceIdentifier] = Field(default_factory=list)
    designation: list[str] = Field(default_factory=list)
    description: str | None = None
    current_frequency: str | None = None
    edition: str | None = None
    contributors: list[Contributor] = Field(default_factory=list)
    production: list[ProductionEvent] = Field(default_factory=list)
    format: Format | None = None
    languages: list[Language] = Field(default_factory=list)
    holdings: list[Holdings] = Field(default_factory=list)
    subjects: list[Subject] = Field(default_factory=list)
    genres: list[Genre] = Field(default_factory=list)
