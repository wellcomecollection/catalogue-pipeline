from pydantic import BaseModel

from models.pipeline.location import DigitalLocation, PhysicalLocation


class Holdings(BaseModel):
    note: str | None = None
    enumeration: list[str] = []
    location: PhysicalLocation | DigitalLocation | None = None
