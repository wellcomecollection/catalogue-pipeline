from typing import Literal

from pydantic import Field

from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Concept, Contributor, Genre, Subject
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Format, Language
from models.pipeline.identifier import SourceIdentifier
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from models.pipeline.serialisable import ElasticsearchModel

WorkType = Literal["Standard", "Series", "Section", "Collection"]


class WorkData(ElasticsearchModel):
    title: str | None = None
    other_identifiers: list[SourceIdentifier] = Field(default_factory=list)
    alternative_titles: list[str] = Field(default_factory=list)
    format: Format | None = None
    description: str | None = None
    physical_description: str | None = None
    lettering: str | None = None
    created_date: Concept | None = None
    subjects: list[Subject] = Field(default_factory=list)
    genres: list[Genre] = Field(default_factory=list)
    contributors: list[Contributor] = Field(default_factory=list)
    thumbnail: DigitalLocation | None = None
    production: list[ProductionEvent] = Field(default_factory=list)
    languages: list[Language] = Field(default_factory=list)
    edition: str | None = None
    notes: list[Note] = Field(default_factory=list)
    duration: int | None = None
    items: list[Item] = Field(default_factory=list)
    holdings: list[Holdings] = Field(default_factory=list)
    collection_path: CollectionPath | None = None
    reference_number: str | None = None
    image_data: list[ImageData] = Field(default_factory=list)
    work_type: WorkType = "Standard"
    current_frequency: str | None = None
    former_frequency: list[str] = Field(default_factory=list)
    designation: list[str] = Field(default_factory=list)
