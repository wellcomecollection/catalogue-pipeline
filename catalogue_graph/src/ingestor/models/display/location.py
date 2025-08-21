from pydantic import BaseModel

from ingestor.models.shared.location import DigitalLocation, PhysicalLocation

from .access_condition import DisplayAccessCondition
from .id_label import DisplayIdLabel
from .license import DisplayLicense
from .location_type import DisplayLocationType


class DisplayLocation(BaseModel):
    locationType: DisplayIdLabel
    license: DisplayLicense | None = None
    accessConditions: list[DisplayAccessCondition]

    @staticmethod
    def from_location(
        location: PhysicalLocation | DigitalLocation,
    ) -> "DisplayDigitalLocation | DisplayPhysicalLocation":
        display_location = DisplayLocation(
            locationType=DisplayLocationType.from_location(location),
            license=DisplayLicense.from_location(location),
            accessConditions=list(DisplayAccessCondition.from_location(location)),
        )

        if isinstance(location, DigitalLocation):
            return DisplayDigitalLocation(
                **display_location.dict(),
                url=location.url,
                credit=location.credit,
                linkText=location.link_text,
            )

        return DisplayPhysicalLocation(
            **display_location.dict(),
            label=location.label,
            shelfmark=location.shelfmark,
        )


class DisplayDigitalLocation(DisplayLocation):
    url: str
    credit: str | None = None
    linkText: str | None = None
    type: str = "DigitalLocation"


class DisplayPhysicalLocation(DisplayLocation):
    label: str
    shelfmark: str | None = None
    type: str = "PhysicalLocation"
