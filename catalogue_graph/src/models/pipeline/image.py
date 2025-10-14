from pydantic import BaseModel

from .identifier import Identified
from .location import DigitalLocation


class ImageData(BaseModel):
    id: Identified
    version: int
    locations: list[DigitalLocation]
