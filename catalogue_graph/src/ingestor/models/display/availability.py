from models.pipeline.id_label import Id

from .id_label import DisplayIdLabel

AVAILABILITY_LABEL_MAPPING = {
    "online": "Online",
    "closed-stores": "Closed stores",
    "open-shelves": "Open shelves",
}


class DisplayAvailability(DisplayIdLabel):
    type: str = "Availability"

    @staticmethod
    def from_availability(availability: Id) -> "DisplayAvailability":
        return DisplayAvailability(
            id=availability.id,
            label=AVAILABILITY_LABEL_MAPPING[availability.id],
            type="Availability",
        )
