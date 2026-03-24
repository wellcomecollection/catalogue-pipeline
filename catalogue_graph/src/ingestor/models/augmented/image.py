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
    average_color_hex: str | None
    aspect_ratio: float | None


class AugmentedImageState(ImageState):
    inferred_data: InferredData


class AugmentedImage(SerialisableModel):
    state: AugmentedImageState
    source: ParentWork
    locations: list[DigitalLocation]
    version: int
    modified_time: str
