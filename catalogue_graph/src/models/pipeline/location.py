from models.pipeline.access_condition import AccessCondition
from models.pipeline.id_label import Id
from models.pipeline.serialisable import ElasticsearchModel


class LocationType(Id):
    pass


OnlineResource = LocationType(id="online-resource")


class Location(ElasticsearchModel):
    location_type: LocationType
    license: Id | None = None
    access_conditions: list[AccessCondition]


class DigitalLocation(Location):
    url: str
    credit: str | None = None
    link_text: str | None = None


class PhysicalLocation(Location):
    label: str
    shelfmark: str | None = None
