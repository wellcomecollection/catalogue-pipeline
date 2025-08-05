from ingestor.models.indexable_work import (
    DisplayIdLabel,
)

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
