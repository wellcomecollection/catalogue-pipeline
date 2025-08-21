from pydantic import BaseModel

from .location import DisplayDigitalLocation, DisplayPhysicalLocation


class DisplayHoldings(BaseModel):
    note: str | None
    enumeration: list[str]
    location: DisplayPhysicalLocation | DisplayDigitalLocation | None
    type: str = "Holdings"
