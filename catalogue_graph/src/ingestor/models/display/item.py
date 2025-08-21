from pydantic import BaseModel

from .identifier import DisplayIdentifier
from .location import DisplayDigitalLocation, DisplayPhysicalLocation


class DisplayItem(BaseModel):
    id: str | None
    identifiers: list[DisplayIdentifier]
    title: str | None = None
    note: str | None = None
    locations: list[DisplayPhysicalLocation | DisplayDigitalLocation] = []
    type: str = "Item"
