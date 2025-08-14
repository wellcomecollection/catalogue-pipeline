from pydantic import BaseModel

from .concept import DisplayConcept, DisplayContributor, DisplayGenre, DisplaySubject
from .holdings import DisplayHoldings
from .id_label import DisplayId, DisplayIdLabel
from .identifier import DisplayIdentifier
from .item import DisplayItem
from .location import DisplayDigitalLocation
from .note import DisplayNote
from .production_event import DisplayProductionEvent
from .relation import DisplayRelation


class DisplayWork(BaseModel):
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
