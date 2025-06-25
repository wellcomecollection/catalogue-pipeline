from models.indexable_work import (
    DisplayDigitalLocation,
    DisplayIdLabel,
    DisplayLocation,
    DisplayPhysicalLocation,
)

from .display_access_condition import get_display_access_condition
from .display_license import get_display_license

LOCATION_LABEL_MAPPING = {
    "closed-stores": "Closed stores",
    "open-shelves": "Open shelves",
    "on-exhibition": "On exhibition",
    "on-order": "On order",
    "iiif-presentation": "IIIF Presentation API",
    "iiif-image": "IIIF Image API",
    "thumbnail-image": "Thumbnail image",
    "online-resource": "Online resource",
}

DIGITAL_LOCATIONS = {
    "iiif-presentation",
    "iiif-image",
    "thumbnail-image",
    "online-resource",
}


def get_display_location_type(location_type_id: str) -> DisplayIdLabel:
    return DisplayIdLabel(
        id=location_type_id,
        label=LOCATION_LABEL_MAPPING[location_type_id],
        type="LocationType",
    )


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
