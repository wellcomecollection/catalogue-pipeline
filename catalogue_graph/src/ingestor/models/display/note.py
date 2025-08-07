from pydantic import BaseModel

from .id_label import DisplayIdLabel


class DisplayNote(BaseModel):
    contents: list[str]
    noteType: DisplayIdLabel
    type: str = "Note"
