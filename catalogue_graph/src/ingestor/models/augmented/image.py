from datetime import datetime

from ingestor.models.indexable.record import IndexableRecord
from models.pipeline.identifier import Identified
from models.pipeline.image_state import ImageState
from models.pipeline.location import DigitalLocation
from models.pipeline.serialisable import SerialisableModel
from models.pipeline.work_data import WorkData


class ParentWork(SerialisableModel):
    id: Identified
    data: WorkData
    version: int


class InferredData(SerialisableModel):
    features: list[float]
    palette_embedding: list[float]
    average_color_hex: str | None = None
    aspect_ratio: float | None = None


class AugmentedImageState(ImageState):
    inferred_data: InferredData
    augmented_time: str | None = None


class AugmentedImage(IndexableRecord):
    state: AugmentedImageState
    source: ParentWork
    locations: list[DigitalLocation]
    version: int
    modified_time: str

    def get_id(self) -> str:
        return self.state.id()

    def get_modified_time(self) -> datetime:
        return datetime.fromisoformat(self.modified_time)
