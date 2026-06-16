"""
Extract access status from field
351 - Restrictions on Access Note
    $f - Standardized terminology for access restriction
https://www.loc.gov/marc/bibliographic/bd351.html
"""

import structlog
from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from models.pipeline.access_status import (
    AccessStatus,
    ByAppointment,
    Open,
    OpenWithAdvisory,
    PermissionRequired,
    Restricted,
    Safeguarded,
    Unavailable,
)

logger = structlog.get_logger(__name__)

"Closed"
"restricted access (data protection act)"
"Donor Permission"
"Cannot Be Produced"
"Temporarily Unavailable"

# 'DATAISSUES', 'PRIVATE'
ACCESS_STATUS_MAPPING = {
    "OPEN": Open,
    "OPENWITHADVISORY": OpenWithAdvisory,
    "RESTRICTED": Restricted,
    "RESTRICTIONSAPPLY": Restricted,
    "PERMISSIONREQUIRED": PermissionRequired,
    "DEACCESSIONED": Unavailable,
    "MISSING": Unavailable,
    "SAFEGUARDED": Safeguarded,
    "BYAPPOINTMENT": ByAppointment,
}


def extract_access_status_value(record: Record) -> str | None:
    """Extract access status from 506 $f.

    Returns the first non-empty value, or None if not present.
    """
    values = non_empty_subfields("506", "f", record)
    return values[0] if values else None


def extract_access_status(record: Record) -> AccessStatus | None:
    status = extract_access_status_value(record)

    if status in ACCESS_STATUS_MAPPING:
        return ACCESS_STATUS_MAPPING[status]
    logger.warn("Unrecognised Axiell access status value", status=status, record=record)
    return None
