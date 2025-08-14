from pydantic import BaseModel

from .identifier import Identifiers, Unidentifiable
from .location import DigitalLocation, PhysicalLocation


class Item(BaseModel):
    id: Identifiers | Unidentifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []
