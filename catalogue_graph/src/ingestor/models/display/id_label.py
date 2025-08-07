from pydantic import BaseModel

from ingestor.models.denormalised.work import IdLabel


class DisplayId(BaseModel):
    id: str
    type: str


class DisplayIdLabel(DisplayId):
    label: str

    @staticmethod
    def from_id_label(data: IdLabel, entity_type: str) -> "DisplayIdLabel":
        return DisplayIdLabel(id=data.id, label=data.label, type=entity_type)
