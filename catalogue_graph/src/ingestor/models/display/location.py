from ingestor.models.display.location_type import (
    DIGITAL_LOCATIONS,
    get_display_location_type,
)
from ingestor.models.indexable_work import (
    DisplayDigitalLocation,
    DisplayLocation,
    DisplayPhysicalLocation,
)

from .access_condition import get_display_access_condition
from .license import get_display_license


def get_display_location(
        raw_location,
) -> DisplayDigitalLocation | DisplayPhysicalLocation:
    location_type = raw_location["locationType"]["id"]
    license_id = raw_location.get("license", {}).get("id")
    is_digital = location_type in DIGITAL_LOCATIONS

    location = DisplayLocation(
        locationType=get_display_location_type(location_type),
        license=get_display_license(license_id) if license_id else None,
        accessConditions=[
            get_display_access_condition(c) for c in raw_location["accessConditions"]
        ],
    )

    if is_digital:
        return DisplayDigitalLocation(
            **location.dict(),
            url=raw_location["url"],
            credit=raw_location.get("credit"),
            linkText=raw_location.get("linkText"),
        )

    return DisplayPhysicalLocation(
        **location.dict(),
        label=raw_location["label"],
        shelfmark=raw_location.get("shelfmark"),
    )
