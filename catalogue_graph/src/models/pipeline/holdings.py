from models.pipeline.location import DigitalLocation, PhysicalLocation
from models.pipeline.serialisable import SerialisableModel


class Holdings(SerialisableModel):
    note: str | None = None
    enumeration: list[str] = []
    location: PhysicalLocation | DigitalLocation | None = None
