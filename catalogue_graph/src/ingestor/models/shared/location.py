from .access_condition import AccessCondition
from .id_label import Id
from .serialisable import ElasticsearchModel


class Location(ElasticsearchModel):
    location_type: Id
    license: Id | None = None
    access_conditions: list[AccessCondition]


class DigitalLocation(Location):
    url: str
    credit: str | None = None
    link_text: str | None = None


class PhysicalLocation(Location):
    label: str
    shelfmark: str | None = None
