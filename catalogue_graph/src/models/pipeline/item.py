from models.pipeline.identifier import Identified, Unidentifiable
from models.pipeline.location import DigitalLocation, PhysicalLocation
from models.pipeline.serialisable import SerialisableModel


class Item(SerialisableModel):
    id: Identified | Unidentifiable
    title: str | None = None
    note: str | None = None
    locations: list[PhysicalLocation | DigitalLocation] = []
