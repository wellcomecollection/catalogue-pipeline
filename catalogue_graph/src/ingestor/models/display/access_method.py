from models.pipeline.access_condition import AccessCondition

from .id_label import DisplayIdLabel

ACCESS_METHOD_LABEL_MAPPING = {
    "OnlineRequest": "Online request",
    "ManualRequest": "Manual request",
    "NotRequestable": "Not requestable",
    "ViewOnline": "View online",
    "OpenShelves": "Open shelves",
}

ACCESS_METHOD_ID_MAPPING = {
    "OnlineRequest": "online-request",
    "ManualRequest": "manual-request",
    "NotRequestable": "not-requestable",
    "ViewOnline": "view-online",
    "OpenShelves": "open-shelves",
}


class DisplayAccessMethod(DisplayIdLabel):
    type: str = "AccessMethod"

    @staticmethod
    def from_access_condition(condition: AccessCondition) -> "DisplayAccessMethod":
        return DisplayAccessMethod(
            id=ACCESS_METHOD_ID_MAPPING[condition.method.type],
            label=ACCESS_METHOD_LABEL_MAPPING[condition.method.type],
        )
