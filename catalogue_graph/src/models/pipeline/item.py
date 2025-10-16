from pydantic import BaseModel

from models.pipeline.identifier import Identified, Unidentifiable
from models.pipeline.location import DigitalLocation, PhysicalLocation


class Item(BaseModel):
    id: Identified | Unidentifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []
