from pydantic import BaseModel

from .location import DigitalLocation, PhysicalLocation


class Holdings(BaseModel):
    note: str | None = None
    enumeration: list[str] = []
    location: PhysicalLocation | DigitalLocation | None = None
