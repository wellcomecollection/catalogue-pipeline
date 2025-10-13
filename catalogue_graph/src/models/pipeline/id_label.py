from pydantic import BaseModel


class Id(BaseModel):
    id: str


class Label(BaseModel):
    label: str


class IdLabel(BaseModel):
    id: str
    label: str
