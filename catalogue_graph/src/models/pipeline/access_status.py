from typing import Literal

from models.pipeline.serialisable import SerialisableModel

AccessStatusValue = Literal[
    "Open",
    "OpenWithAdvisory",
    "Restricted",
    "Safeguarded",
    "ByAppointment",
    "TemporarilyUnavailable",
    "Unavailable",
    "Closed",
    "PermissionRequired",
    "LicensedResources",
]


class AccessStatusRelationship(SerialisableModel):
    type: str


class AccessStatus(SerialisableModel):
    type: AccessStatusValue
    relationship: AccessStatusRelationship | None = None

    @property
    def is_available(self) -> bool:
        """Open, or a licensed resource that is the item itself rather than a related one."""
        if self.type in ("Open", "OpenWithAdvisory"):
            return True
        return (
            self.type == "LicensedResources"
            and self.relationship is not None
            and self.relationship.type == "Resource"
        )

    @property
    def has_restrictions(self) -> bool:
        return self.type in (
            "OpenWithAdvisory",
            "Restricted",
            "ByAppointment",
            "Closed",
            "PermissionRequired",
            "Safeguarded",
        )


Open = AccessStatus(type="Open")
OpenWithAdvisory = AccessStatus(type="OpenWithAdvisory")
Restricted = AccessStatus(type="Restricted")
Safeguarded = AccessStatus(type="Safeguarded")
ByAppointment = AccessStatus(type="ByAppointment")
TemporarilyUnavailable = AccessStatus(type="TemporarilyUnavailable")
Unavailable = AccessStatus(type="Unavailable")
Closed = AccessStatus(type="Closed")
PermissionRequired = AccessStatus(type="PermissionRequired")

#  TODO: (Resource vs RelatedResource)
#  Scala code says this is not exposed the public API,
#  but we need it for the "available online" filter.
#  I wonder if that is actually true, and we can get
#  rid of the whole thing?
#  All EBSCO records are currently "Resource"
Resource = AccessStatusRelationship(type="Resource")
LicensedResource = AccessStatus(type="LicensedResources", relationship=Resource)
