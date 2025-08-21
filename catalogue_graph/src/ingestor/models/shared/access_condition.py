from pydantic import BaseModel


class Type(BaseModel):
    type: str


class AccessCondition(BaseModel):
    method: Type
    status: Type | None = None
    terms: str | None = None
    note: str | None = None
