from pydantic import BaseModel


class DisplayId(BaseModel):
    id: str
    type: str


class DisplayIdLabel(DisplayId):
    label: str
