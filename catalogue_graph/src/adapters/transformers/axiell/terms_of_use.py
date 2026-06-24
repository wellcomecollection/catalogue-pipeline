from datetime import date

import structlog
from pymarc.record import Record

from adapters.transformers.axiell.access_status import extract_access_status
from adapters.transformers.marc.common import first_non_empty_subfield
from adapters.transformers.marc.identifier import extract_id
from models.pipeline.access_status import Closed, PermissionRequired, Restricted
from models.pipeline.id_label import IdLabel
from models.pipeline.note import Note

logger = structlog.get_logger(__name__)

TERMS_OF_USE = IdLabel(id="terms-of-use", label="Terms of use")


class TermsOfUseNote(Note):
    note_type: IdLabel = TERMS_OF_USE


def _try_parse_date(s: str) -> date | None:
    """Try to parse date in d/M/yyyy format (e.g. 01/01/2039). Return `None` if not valid date."""

    try:
        d, m, y = s.split("/")
        parsed = date(int(y), int(m), int(d))
        return parsed
    except (ValueError, TypeError):
        logger.warning("Could not parse date", value=s)

    return None


def extract_closed_until_date(record: Record) -> date | None:
    value = first_non_empty_subfield("506", "g", record)
    return _try_parse_date(value) if value else None


def extract_restricted_until_date(record: Record) -> date | None:
    value = first_non_empty_subfield("540", "g", record)
    return _try_parse_date(value) if value else None


def extract_access_conditions(record: Record) -> str | None:
    value = first_non_empty_subfield("506", "a", record)

    # Normalise conditions: strip trailing whitespace, then ensure the text ends with a period
    if value:
        stripped = value.strip()
        value = stripped if stripped.endswith(".") else stripped + "."

    return value


def _display_date(d: date) -> str:
    """Format as e.g. '1 January 2021'."""
    return f"{d.day} {d.strftime('%B %Y')}"


def _contains_date(text: str, d: date) -> bool:
    """Return True if text contains 'until {date}' in any recognised format.

    Normalises ordinal suffixes (1st → 1, 2nd → 2, 3rd → 3, *th → *) before checking.
    """
    normalised = (
        text.replace("1st", "1")
        .replace("2nd", "2")
        .replace("3rd", "3")
        .replace("th", "")
    )
    return any(
        f"until {fmt}" in normalised
        for fmt in (_display_date(d), d.strftime("%d/%m/%Y"))
    )


def _has_restrictions(text: str) -> bool:
    lower = text.lower()
    return "restricted" in lower or "restrictions" in lower


def extract_terms_of_use(record: Record) -> Note | None:
    conditions = extract_access_conditions(record)
    access_status = extract_access_status(record)
    closed_until = extract_closed_until_date(record)
    restricted_until = extract_restricted_until_date(record)

    # No conditions and no dates: nothing useful to output
    if not conditions and not closed_until and not restricted_until:
        return None

    # Conditions with no dates: return them as-is
    if conditions and not closed_until and not restricted_until:
        return TermsOfUseNote(contents=conditions)

    # Closed status with a closedUntil date
    if access_status == Closed and closed_until:
        if conditions:
            if "closed" in conditions.lower() and _contains_date(
                conditions, closed_until
            ):
                return TermsOfUseNote(contents=conditions)
            return TermsOfUseNote(
                contents=f"{conditions} Closed until {_display_date(closed_until)}.",
            )
        return TermsOfUseNote(
            contents=f"Closed until {_display_date(closed_until)}.",
        )

    # Restricted status with a restrictedUntil date
    if access_status == Restricted and restricted_until:
        if conditions:
            if "restricted" in conditions.lower() and _contains_date(
                conditions, restricted_until
            ):
                return TermsOfUseNote(contents=conditions)
            return TermsOfUseNote(
                contents=f"{conditions} Restricted until {_display_date(restricted_until)}.",
            )
        return TermsOfUseNote(
            contents=f"Restricted until {_display_date(restricted_until)}.",
        )

    # PermissionRequired with a restrictedUntil date where conditions already mention
    # both permission and restrictions
    if access_status == PermissionRequired and restricted_until and conditions:
        if "permission" in conditions.lower() and _has_restrictions(conditions):
            if _contains_date(conditions, restricted_until):
                return TermsOfUseNote(contents=conditions)
            return TermsOfUseNote(
                contents=f"{conditions} Restricted until {_display_date(restricted_until)}.",
            )

    # Catch-all: log a warning and combine what we have. This affects very few
    # records and typically reflects a data issue in the source system.
    logger.warning(
        "Unclear how to create a TermsOfUse note",
        record_id=extract_id(record),
    )
    parts = [
        conditions,
        f"Restricted until {_display_date(restricted_until)}."
        if restricted_until
        else None,
        f"Closed until {_display_date(closed_until)}." if closed_until else None,
    ]
    non_empty = [p for p in parts if p]
    if not non_empty:
        return None
    return TermsOfUseNote(contents=" ".join(non_empty))
