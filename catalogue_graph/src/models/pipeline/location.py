from typing import Literal

from models.pipeline.access_condition import AccessCondition
from models.pipeline.id_label import Id
from models.pipeline.serialisable import SerialisableModel


class LocationType(Id):
    pass


OnlineResource = LocationType(id="online-resource")

LocationDiscriminator = Literal["DigitalLocation", "PhysicalLocation"]


class Location(SerialisableModel):
    # Required for Scala deserialiser
    type: LocationDiscriminator
    location_type: LocationType
    license: Id | None = None
    access_conditions: list[AccessCondition]


class DigitalLocation(Location):
    type: LocationDiscriminator = "DigitalLocation"
    url: str
    credit: str | None = None
    link_text: str | None = None


class PhysicalLocation(Location):
    type: LocationDiscriminator = "PhysicalLocation"
    label: str
    shelfmark: str | None = None
