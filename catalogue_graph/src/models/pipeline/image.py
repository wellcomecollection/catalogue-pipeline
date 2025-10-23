from models.pipeline.identifier import Identified
from models.pipeline.location import DigitalLocation
from models.pipeline.serialisable import SerialisableModel


class ImageData(SerialisableModel):
    id: Identified
    version: int
    locations: list[DigitalLocation]
