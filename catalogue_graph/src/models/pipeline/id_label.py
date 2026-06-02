from models.pipeline.serialisable import SerialisableModel


class Id(SerialisableModel):
    id: str


class Label(SerialisableModel):
    label: str


class IdLabel(SerialisableModel):
    id: str
    label: str


class Language(IdLabel):
    pass
