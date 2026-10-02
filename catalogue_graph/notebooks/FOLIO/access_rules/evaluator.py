"""Evaluates the draft FOLIO access condition table (decision_table.md) for one item.

Discovery code for platform#6589: it doesn't ship.
"""

import csv
from dataclasses import dataclass
from functools import cache
from pathlib import Path

from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import AccessMethod
from models.pipeline.access_status import AccessStatus

HERE = Path(__file__).parent

# Substrings Sierra uses to decide whether a display note is about access (SierraItemAccess.scala).
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
class FolioItemState:
    item_status: str | None = None
    statistical_code: str | None = None
    loan_type: str | None = None
    library: str | None = None
    location_code: str | None = None
    material_type: str | None = None
    open_request: bool | None = None
    display_note: str | None = None
    exhibition_text: str | None = None


@dataclass(frozen=True)
class Result:
    access_condition: AccessCondition
    item_note: str | None
    rule_id: str


def provisional_location_type(
    library: str | None, location_code: str | None
) -> str | None:
    # Stand-in until #6584 defines FOLIO location types.
    if location_code == "migration":
        return None
    return {
        "Closed stores": "closed-stores",
        "Offsite (DS)": "closed-stores",
        "Open shelves": "open-shelves",
        "On Exhibition": "on-exhibition",
    }.get(library or "")


@cache
def rules() -> list[dict[str, str]]:
    with open(HERE / "rules.csv", newline="") as f:
        return list(csv.DictReader(f))


@cache
def notes() -> dict[str, str]:
    with open(HERE / "notes.csv", newline="") as f:
        return {row["note"]: row["text"] for row in csv.DictReader(f)}


@cache
def ruled_out() -> list[dict[str, str]]:
    with open(HERE / "ruled_out.csv", newline="") as f:
        return list(csv.DictReader(f))


def inputs(state: FolioItemState) -> dict[str, str]:
    """The value of each rules.csv input column for this item."""
    return {
        "item_status": state.item_status or "",
        "statistical_code": state.statistical_code or "",
        "loan_type": state.loan_type or "",
        "library": state.library or "",
        "location_code": state.location_code or "",
        # Not a FOLIO field: derived from the location until #6584 defines location types.
        "location_type": provisional_location_type(state.library, state.location_code)
        or "",
        "material_type": state.material_type or "",
        "open_request": "true" if state.open_request else "",
    }


def matches(rule: dict[str, str], values: dict[str, str]) -> bool:
    return all(
        not rule[column] or value in rule[column].split("|")
        for column, value in values.items()
    )


def _contains_any(text: str, hints: tuple[str, ...]) -> bool:
    return any(h in text.lower() for h in hints)


def _rule_note(rule: dict[str, str], state: FolioItemState) -> str | None:
    if rule["note"] == "exhibition":
        return state.exhibition_text or notes()["contact"]
    return notes()[rule["note"]] if rule["note"] else None


def _apply_note_precedence(
    rule: dict[str, str], note: str | None, display_note: str | None
) -> tuple[str | None, str | None]:
    """Returns (access condition note, item note), per the rule's display_note column."""
    if not display_note:
        return note, None
    if rule["display_note"] == "display-note-wins-if-manual-request" and _contains_any(
        display_note, MANUAL_REQUEST_NOTE_HINTS
    ):
        return display_note, None
    # condition-note-wins: an access-type display note only fills an empty note.
    if _contains_any(display_note, ACCESS_NOTE_HINTS) or display_note == note:
        return note or display_note, None
    return note, display_note


def evaluate(state: FolioItemState) -> Result:
    values = inputs(state)
    rule = next(r for r in rules() if matches(r, values))
    note, item_note = _apply_note_precedence(
        rule, _rule_note(rule, state), state.display_note
    )

    condition = AccessCondition(
        method=AccessMethod(type=rule["method"]),  # type: ignore[arg-type]
        status=AccessStatus(type=rule["status"]) if rule["status"] else None,  # type: ignore[arg-type]
        note=note,
    )
    return Result(
        access_condition=condition, item_note=item_note, rule_id=rule["rule_id"]
    )
