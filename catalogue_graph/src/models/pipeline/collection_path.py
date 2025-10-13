from pydantic import BaseModel


class CollectionPath(BaseModel):
    path: str
    label: str | None = None
