from typing import Self

from pydantic import BaseModel


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    partOf: list[Self] | None = None
    totalParts: int
    # totalDescendentParts: int # TODO: Is this field needed?
    type: str = "Work"
