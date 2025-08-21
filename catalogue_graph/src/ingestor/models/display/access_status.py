from typing import Optional

from ingestor.models.shared.access_condition import AccessCondition

from .id_label import DisplayIdLabel

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


class DisplayAccessStatus(DisplayIdLabel):
    type: str = "AccessStatus"

    @staticmethod
    def from_access_condition(
        condition: AccessCondition,
    ) -> Optional["DisplayAccessStatus"]:
        if condition.status is None:
            return None

        return DisplayAccessStatus(
            id=ACCESS_STATUS_ID_MAPPING[condition.status.type],
            label=ACCESS_STATUS_LABEL_MAPPING[condition.status.type],
        )
