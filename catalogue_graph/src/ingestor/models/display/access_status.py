from ingestor.models.indexable_work import DisplayIdLabel

ACCESS_STATUS_ID_MAPPING = {
    "Open": "open",
    "OpenWithAdvisory": "open-with-advisory",
    "Restricted": "restricted",
    "Safeguarded": "safeguarded",
    "ByAppointment": "by-appointment",
    "TemporarilyUnavailable": "temporarily-unavailable",
    "Unavailable": "unavailable",
    "Closed": "closed",
    "LicensedResources": "licensed-resources",
    "PermissionRequired": "permission-required",
}


ACCESS_STATUS_LABEL_MAPPING = {
    "Open": "Open",
    "OpenWithAdvisory": "Open with advisory",
    "Restricted": "Restricted",
    "Safeguarded": "Safeguarded",
    "ByAppointment": "By appointment",
    "TemporarilyUnavailable": "Temporarily unavailable",
    "Unavailable": "Unavailable",
    "Closed": "Closed",
    "LicensedResources": "Licensed resources",
    "PermissionRequired": "Permission required",
}


def get_display_access_status(raw_id: str) -> DisplayIdLabel:
    return DisplayIdLabel(
        id=ACCESS_STATUS_ID_MAPPING[raw_id],
        label=ACCESS_STATUS_LABEL_MAPPING[raw_id],
        type="AccessStatus",
    )
