from models.pipeline.serialisable import SerialisableModel


class CollectionPath(SerialisableModel):
    path: str
    label: str | None = None
