from models.pipeline.serialisable import SerialisableModel


class Id(SerialisableModel):
    id: str


class Label(SerialisableModel):
    label: str


class IdLabel(SerialisableModel):
    id: str
    label: str


class Format(IdLabel):
    pass


EBooks = Format(id="v", label="E-Books")
EJournals = Format(id="j", label="E-Journals")


class Language(IdLabel):
    pass
