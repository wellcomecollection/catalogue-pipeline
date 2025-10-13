from typing import Literal

from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Concept, Contributor, Genre, Subject
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import IdLabel
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
