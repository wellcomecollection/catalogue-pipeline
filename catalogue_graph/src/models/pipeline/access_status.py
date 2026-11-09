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

# Only a Resource makes the work available online. A RelatedResource links to
# something about the work, such as a publisher page. The API shows both as
# licensed-resources.
Resource = AccessStatusRelationship(type="Resource")
RelatedResource = AccessStatusRelationship(type="RelatedResource")
LicensedResource = AccessStatus(type="LicensedResources", relationship=Resource)
LicensedRelatedResource = AccessStatus(
    type="LicensedResources", relationship=RelatedResource
)
