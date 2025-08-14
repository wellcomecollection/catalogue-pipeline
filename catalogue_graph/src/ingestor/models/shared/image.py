from pydantic import BaseModel

from .identifier import Identifiers
from .location import DigitalLocation


class ImageData(BaseModel):
    id: Identifiers
    version: int
    locations: list[DigitalLocation]
