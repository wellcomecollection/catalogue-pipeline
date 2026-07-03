"""
Extract access status from field
506 - Restrictions on Access Note
    $f - Standardized terminology for access restriction
https://www.loc.gov/marc/bibliographic/bd506.html
"""

from datetime import date

import structlog
from pymarc.record import Record

from adapters.transformers.axiell.access_dates import extract_closed_until_date
from adapters.transformers.marc.common import non_empty_subfields
from adapters.transformers.marc.identifier import extract_id
from models.pipeline.access_status import (
    AccessStatus,
    ByAppointment,
    Closed,
    Open,
    OpenWithAdvisory,
    PermissionRequired,
    Restricted,
    Safeguarded,
    Unavailable,
)

logger = structlog.get_logger(__name__)

# TODO: Axiell has two more statuses:
#  * DATAISSUES: Equivalent of the CALM 'Data issues' status, which is not handled in the Scala code
#  * PRIVATE: No CALM equivalent. At the moment, only one record has this status (collect:100003386)

# CALM has a few more statuses which currently don't exist in Axiell:
#  * Closed: Mapped to Closed
#  * Donor Permission: Mapped to PermissionRequired
#  * Cannot Be Produced: Mapped to Unavailable
#  * Temporarily Unavailable: Mapped to TemporarilyUnavailable
#  * restricted access (data protection act): Mapped to Restricted
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

    # Unlike CALM, Axiell does not currently have a CLOSED status value. To determine if an item is closed,
    # we use the 'closed until' date instead. If this date is in the future, the item is closed.
    # This approach produces a ~98% match with the CALM transformer.
    closed_until = extract_closed_until_date(record)
    if closed_until and closed_until >= date.today():
        return Closed

    if status is not None:
        logger.warning(
            "Unrecognised Axiell access status value",
            status=status,
            record_id=extract_id(record),
        )
    return None
