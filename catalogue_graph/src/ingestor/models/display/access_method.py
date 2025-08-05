from ingestor.models.indexable_work import DisplayIdLabel

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


def get_display_access_method(raw_id: str) -> DisplayIdLabel:
    return DisplayIdLabel(
        id=ACCESS_METHOD_ID_MAPPING[raw_id],
        label=ACCESS_METHOD_LABEL_MAPPING[raw_id],
        type="AccessMethod",
    )
