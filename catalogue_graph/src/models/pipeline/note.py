from models.pipeline.id_label import IdLabel
from models.pipeline.serialisable import SerialisableModel


class Note(SerialisableModel):
    note_type: IdLabel
    contents: str
