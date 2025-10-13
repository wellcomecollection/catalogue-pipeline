from pydantic import BaseModel

from models.pipeline.identifier import Identifiers, Unidentifiable
from models.pipeline.location import DigitalLocation, PhysicalLocation


class Item(BaseModel):
    id: Identifiers | Unidentifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []
