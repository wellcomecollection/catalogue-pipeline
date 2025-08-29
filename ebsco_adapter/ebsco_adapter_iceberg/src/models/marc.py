from pydantic import BaseModel


class MarcRecord(BaseModel):
    id: str
    title: str
