from pymarc.record import Record

from adapters.transformers.axiell.access_status import extract_access_status
from adapters.transformers.marc.common import (
    first_non_empty_subfield,
)
from models.pipeline.note import Note


def extract_closed_until_date(record: Record) -> str | None:
    # d/M/yyyy
    closed_until = first_non_empty_subfield("506", "g", record)

    if not closed_until:
        return None

    return closed_until


def extract_restricted_until_date(record: Record) -> str | None:
    closed_until = first_non_empty_subfield("540", "g", record)

    if not closed_until:
        return None

    return closed_until


def extract_access_conditions(record: Record) -> str | None:
    return first_non_empty_subfield("506", "a", record)


def extract_terms_of_use(record: Record) -> Note:
    access_conditions = extract_access_conditions(record)
    access_status = extract_access_status(record)
    closed_until_date = extract_closed_until_date(record)
    restricted_until_date = extract_restricted_until_date(record)

    # If some conditions AND some status:
    #    Conditions. Status.
    # If some conditions AND status == closed AND some closedUntil AND closedUntil included in conditions:
    #    Conditions.
    # If some conditions AND status == closed AND some closedUntil AND closedUntil NOT included in conditions:
    #    Conditions. Closed until closedUntil.
    # If no conditions and status == closed and some closedUntil:
    #    Closed until closedUntil
    # If some conditions AND status == restricted AND some restrictedUntil AND restrictedUntil included in conditions:
    #    Conditions.
    # If some conditions AND status == restricted AND some restrictedUntil AND restrictedUntil NOT included in conditions:
    #    Conditions. Restricted until restrictedUntil.
    # If no conditions and status == restricted and some closedUntil:
    #    Restricted until restrictedUntil.
    # If some conditions AND status == permission required AND some restrictedUntil
    #
