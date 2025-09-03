from datetime import datetime
from typing import Literal

from pydantic import BaseModel, field_validator

from ingestor.models.shared.concept import Concept, Contributor, Genre, Subject
from ingestor.models.shared.holdings import Holdings
from ingestor.models.shared.id_label import Id, IdLabel
from ingestor.models.shared.identifier import (
    SourceIdentifier,
)
from ingestor.models.shared.image import ImageData
from ingestor.models.shared.item import Item
from ingestor.models.shared.location import DigitalLocation
from ingestor.models.shared.merge_candidate import MergeCandidate
from ingestor.models.shared.note import Note
from ingestor.models.shared.production import ProductionEvent
from ingestor.models.shared.serialisable import ElasticsearchModel
from utils.types import WorkType


class CollectionPath(BaseModel):
    path: str
    label: str | None = None


class DenormalisedWorkData(ElasticsearchModel):
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
    work_type: WorkType = "Work"
    current_frequency: str | None = None
    former_frequency: list[str] = []
    designation: list[str] = []

    @field_validator("work_type", mode="before")
    @classmethod
    def convert_denormalised_type(
        cls, value: WorkType | Literal["Standard"]
    ) -> WorkType:
        # In the denormalised index, the 'Work' type is called 'Standard'
        if value == "Standard":
            return "Work"

        return value


class DenormalisedWorkState(ElasticsearchModel):
    source_identifier: SourceIdentifier
    canonical_id: str
    merged_time: datetime
    source_modified_time: datetime
    availabilities: list[Id]
    merge_candidates: list[MergeCandidate]


class DenormalisedWork(BaseModel):
    data: DenormalisedWorkData
    state: DenormalisedWorkState
    version: int
