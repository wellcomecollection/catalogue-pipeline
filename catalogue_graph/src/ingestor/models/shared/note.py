from .id_label import IdLabel
from .serialisable import ElasticsearchModel


class Note(ElasticsearchModel):
    note_type: IdLabel
    contents: str
