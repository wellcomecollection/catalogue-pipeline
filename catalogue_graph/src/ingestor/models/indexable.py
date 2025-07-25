from pydantic import BaseModel


class DisplayId(BaseModel):
    id: str
    type: str


class DisplayIdLabel(DisplayId):
    label: str


class DisplayIdentifierType(DisplayIdLabel):
    type: str = "IdentifierType"


class DisplayIdentifier(BaseModel):
    value: str
    type: str = "Identifier"
    identifierType: DisplayIdentifierType
