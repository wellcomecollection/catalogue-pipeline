from pydantic import BaseModel

from models.pipeline.location import DigitalLocation, PhysicalLocation

from .access_condition import DisplayAccessCondition
from .id_label import DisplayIdLabel
from .license import DisplayLicense
from .location_type import DisplayLocationType


class DisplayLocation(BaseModel):
    locationType: DisplayIdLabel
    license: DisplayLicense | None = None
    accessConditions: list[DisplayAccessCondition]

    @staticmethod
    def _base_from_location(
        location: PhysicalLocation | DigitalLocation,
    ) -> "DisplayLocation":
        return DisplayLocation(
            locationType=DisplayLocationType.from_location(location),
            license=DisplayLicense.from_location(location),
            accessConditions=list(DisplayAccessCondition.from_location(location)),
        )

    @staticmethod
    def from_location(
        location: PhysicalLocation | DigitalLocation,
    ) -> "DisplayDigitalLocation | DisplayPhysicalLocation":
        if isinstance(location, DigitalLocation):
            return DisplayDigitalLocation.from_digital_location(location)

        return DisplayPhysicalLocation.from_physical_location(location)


class DisplayDigitalLocation(DisplayLocation):
    url: str
    credit: str | None = None
    linkText: str | None = None
    type: str = "DigitalLocation"

    @classmethod
    def from_digital_location(
        cls, location: DigitalLocation
    ) -> "DisplayDigitalLocation":
        display_location = cls._base_from_location(location)
        return DisplayDigitalLocation(
            **display_location.dict(),
            url=location.url,
            credit=location.credit,
            linkText=location.link_text,
        )


class DisplayPhysicalLocation(DisplayLocation):
    label: str
    shelfmark: str | None = None
    type: str = "PhysicalLocation"

    @classmethod
    def from_physical_location(
        cls, location: PhysicalLocation
    ) -> "DisplayPhysicalLocation":
        display_location = cls._base_from_location(location)
        return DisplayPhysicalLocation(
            **display_location.dict(),
            label=location.label,
            shelfmark=location.shelfmark,
        )
