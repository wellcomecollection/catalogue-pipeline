from typing import Literal

from models.pipeline.access_condition import AccessCondition
from models.pipeline.availability import CLOSED_STORES, ONLINE, OPEN_SHELVES
from models.pipeline.id_label import Id
from models.pipeline.serialisable import SerialisableModel


class LocationType(Id):
    pass


OnlineResource = LocationType(id="online-resource")
ClosedStores = LocationType(id="closed-stores")

LocationDiscriminator = Literal["DigitalLocation", "PhysicalLocation"]


class Location(SerialisableModel):
    # Required for Scala deserialiser
    type: LocationDiscriminator
    location_type: LocationType
    license: Id | None = None
    access_conditions: list[AccessCondition]

    @property
    def is_available(self) -> bool:
        return any(condition.is_available for condition in self.access_conditions)

    @property
    def has_restrictions(self) -> bool:
        return any(condition.has_restrictions for condition in self.access_conditions)

    def availability(self, in_other_library: bool) -> Id | None:
        """How this location makes the item available, if it does."""
        return None


class DigitalLocation(Location):
    type: LocationDiscriminator = "DigitalLocation"
    url: str
    credit: str | None = None
    link_text: str | None = None
    created_date: str | None = None

    def availability(self, in_other_library: bool) -> Id | None:
        return ONLINE if self.is_available else None


class PhysicalLocation(Location):
    type: LocationDiscriminator = "PhysicalLocation"
    label: str
    shelfmark: str | None = None

    def availability(self, in_other_library: bool) -> Id | None:
        if self.location_type.id == "open-shelves":
            return OPEN_SHELVES
        if self.location_type.id == "closed-stores" and not in_other_library:
            return CLOSED_STORES
        return None
