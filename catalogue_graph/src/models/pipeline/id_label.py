from pydantic import BaseModel


class Id(BaseModel):
    id: str


class Label(BaseModel):
    label: str


class IdLabel(BaseModel):
    id: str
    label: str


class Format(IdLabel):
    pass


EBooks = Format(id="v", label="E-Books")
EJournals = Format(id="j", label="E-Journals")


class Language(IdLabel):
    pass
