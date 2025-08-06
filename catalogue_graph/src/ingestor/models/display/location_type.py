from ingestor.models.denormalised.work import Location

from .id_label import DisplayIdLabel

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


class DisplayLocationType(DisplayIdLabel):
    type: str = "LocationType"

    @staticmethod
    def from_location(location: Location) -> "DisplayLocationType":
        return DisplayLocationType(
            id=location.locationType.id,
            label=LOCATION_LABEL_MAPPING[location.locationType.id],
        )
