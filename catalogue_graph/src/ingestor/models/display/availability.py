from models.pipeline.id_label import Id

from .id_label import DisplayIdLabel

ONLINE_AVAILABILITY_ID = "online"


def is_available_online(availability_ids: list[str]) -> bool:
    return ONLINE_AVAILABILITY_ID in availability_ids


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
