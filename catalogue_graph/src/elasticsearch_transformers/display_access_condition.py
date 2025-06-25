from models.indexable_work import DisplayAccessCondition, DisplayIdLabel

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


def get_display_access_condition(raw_condition: dict):
    method, status = None, None
    if "method" in raw_condition:
        method = get_display_access_method(raw_condition["method"]["type"])
    if "status" in raw_condition:
        status = get_display_access_status(raw_condition["status"]["type"])

    return DisplayAccessCondition(
        method=method,
        status=status,
        terms=raw_condition.get("terms"),
        note=raw_condition.get("note"),
    )
