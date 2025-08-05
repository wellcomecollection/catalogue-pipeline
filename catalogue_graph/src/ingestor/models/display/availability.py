from ingestor.models.indexable_work import DisplayIdLabel

AVAILABILITY_LABEL_MAPPING = {
    "online": "Online",
    "closed-stores": "Closed stores",
    "open-shelves": "Open shelves"
}


def get_display_availability(raw_id: str) -> DisplayIdLabel:
    return DisplayIdLabel(
        id=raw_id,
        label=AVAILABILITY_LABEL_MAPPING[raw_id],
        type="Availability"
    )
