import re
from datetime import date

import structlog
from pymarc.record import Record

from adapters.transformers.axiell.access_status import extract_access_status
from adapters.transformers.axiell.dates import extract_restricted_or_closed_until_date
from adapters.transformers.marc.common import first_non_empty_subfield
from adapters.transformers.marc.identifier import extract_id
from models.pipeline.access_status import (
    AccessStatus,
    Closed,
    PermissionRequired,
    Restricted,
)

logger = structlog.get_logger(__name__)


def extract_access_conditions(record: Record) -> str | None:
    value = first_non_empty_subfield("506", "a", record)

    # Normalise conditions: strip trailing whitespace, then ensure the text ends with a period
    if value:
        stripped = value.strip()
        if not stripped:
            return None
        value = stripped if stripped.endswith(".") else stripped + "."

    return value


def _display_date(d: date) -> str:
    """Format date. Example output: `1 January 2021`."""
    return f"{d.day} {d.strftime('%B %Y')}"


def _contains_date(text: str, d: date) -> bool:
    """Return True if text contains 'until {date}' in any recognised format.

    Normalises ordinal suffixes (1st → 1, 2nd → 2, 3rd → 3, *th → *) before checking.
    """
    normalised = re.sub(r"(\d+)(st|nd|rd|th)", r"\1", text)
    return any(
        f"until {fmt}" in normalised
        for fmt in (_display_date(d), d.strftime("%d/%m/%Y"))
    )


def _has_restrictions(text: str) -> bool:
    lower = text.lower()
    return "restricted" in lower or "restrictions" in lower


def _until_label(
    access_status: AccessStatus | None, conditions: str | None
) -> str | None:
    """The word that introduces the date.

    Axiell holds the restricted-until and closed-until date in the same 506 $g
    subfield, so the status is what says which of the two it is. A status that
    says neither cannot label a date. PermissionRequired counts as restricted,
    but only when the conditions already talk about permission and restrictions.
    """
    if access_status == Closed:
        return "Closed"
    if access_status == Restricted:
        return "Restricted"
    if (
        access_status == PermissionRequired
        and conditions
        and "permission" in conditions.lower()
        and _has_restrictions(conditions)
    ):
        return "Restricted"
    return None


def _already_stated(conditions: str, label: str, until: date) -> bool:
    """Whether the conditions text already carries both the word and the date."""
    stated = (
        _has_restrictions(conditions)
        if label == "Restricted"
        else label.lower() in conditions.lower()
    )
    return stated and _contains_date(conditions, until)


def extract_terms_of_use(record: Record) -> str | None:
    """Construct a 'terms of use' note from 506 $a, $f and $g."""
    conditions = extract_access_conditions(record)
    until = extract_restricted_or_closed_until_date(record)

    if not until:
        return conditions

    label = _until_label(extract_access_status(record), conditions)
    if label is None:
        # Catch-all: a date with no status to label it. Keep the conditions and
        # drop the date rather than assert an access status the record does not
        # give. This affects very few records and typically reflects a data
        # issue in the source system.
        logger.warning(
            "Unclear how to create a 'terms of use' note",
            record_id=extract_id(record),
        )
        return conditions

    sentence = f"{label} until {_display_date(until)}."
    if not conditions:
        return sentence
    if _already_stated(conditions, label, until):
        return conditions
    return f"{conditions} {sentence}"
