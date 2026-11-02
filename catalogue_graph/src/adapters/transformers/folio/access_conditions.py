"""FOLIO item access conditions, from the decision table in docs/folio/access-conditions (platform#6589).

The rows live in access_rules/rules.csv as data, so a change agreed to the table is a change to that file.
"""

import csv
from dataclasses import dataclass
from functools import cache
from pathlib import Path
from typing import cast

from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import AccessMethod, AccessMethodValue
from models.pipeline.access_status import AccessStatus, AccessStatusValue

RULES_DIR = Path(__file__).parent / "access_rules"

INPUT_COLUMNS = (
    "item_status",
    "statistical_code",
    "loan_type",
    "library",
    "location_type",
    "location_code",
    "material_type",
    # Only the items service (platform#6654) knows about open requests, so it's never a field.
    "open_request",
)

# Sierra's substrings for a display note about access (SierraItemAccess.scala).
ACCESS_NOTE_HINTS = (
    "unavailable",
    "access",
    "please contact",
    "@wellcomecollection.org",
    "offsite",
    "shelved at",
)
MANUAL_REQUEST_NOTE_HINTS = (
    "needs to be ordered",
    "to view this item",
    "to view it",
    "physical access",
    "physical copy",
    "why you need access",
    "details of your request",
    "to view please contact",
    "if you would like to see",
)


@dataclass(frozen=True)
class FolioItemData:
    item_status: str | None = None
    statistical_code: str | None = None
    loan_type: str | None = None
    library: str | None = None
    location_code: str | None = None
    location_type: str | None = None
    material_type: str | None = None
    display_note: str | None = None


@cache
def rules() -> list[dict[str, str]]:
    with open(RULES_DIR / "rules.csv", newline="") as f:
        return list(csv.DictReader(f))


@cache
def notes() -> dict[str, str]:
    with open(RULES_DIR / "notes.csv", newline="") as f:
        return {row["note"]: row["text"] for row in csv.DictReader(f)}


def _matches(rule: dict[str, str], data: FolioItemData) -> bool:
    # A blank cell matches anything; a column with no field never matches a value.
    return all(
        not rule[column] or getattr(data, column, None) in rule[column].split("|")
        for column in INPUT_COLUMNS
    )


def _contains_any(text: str, hints: tuple[str, ...]) -> bool:
    lowered = text.lower()
    return any(h in lowered for h in hints)


def _rule_note(rule: dict[str, str]) -> str | None:
    # The exhibition text is a staff-only note in FOLIO, so the contact note stands in.
    if rule["note"] == "exhibition":
        return notes()["contact"]
    return notes()[rule["note"]] if rule["note"] else None


def is_access_note(display_note: str) -> bool:
    """Whether a display note is access information, so it belongs on the access condition."""
    return _contains_any(display_note, ACCESS_NOTE_HINTS)


def _access_condition_note(
    rule: dict[str, str], rule_note: str | None, display_note: str | None
) -> str | None:
    """The access condition's note, from the rule's note and the item's display note."""
    if not display_note:
        return rule_note
    if rule["display_note"] == "display-note-wins-if-manual-request" and _contains_any(
        display_note, MANUAL_REQUEST_NOTE_HINTS
    ):
        return display_note
    # condition-note-wins: a display note about access only fills an empty note.
    if is_access_note(display_note):
        return rule_note or display_note
    return rule_note


def evaluate(data: FolioItemData) -> AccessCondition:
    """Apply the first matching row; the table ends in a catch-all, so one always matches."""
    rule = next(r for r in rules() if _matches(r, data))
    return AccessCondition(
        method=AccessMethod(type=cast(AccessMethodValue, rule["method"])),
        status=AccessStatus(type=cast(AccessStatusValue, rule["status"]))
        if rule["status"]
        else None,
        note=_access_condition_note(rule, _rule_note(rule), data.display_note),
    )
