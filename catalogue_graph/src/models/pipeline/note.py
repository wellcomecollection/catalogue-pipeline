from models.pipeline.id_label import IdLabel
from models.pipeline.serialisable import ElasticsearchModel


class Note(ElasticsearchModel):
    note_type: IdLabel
    contents: str
