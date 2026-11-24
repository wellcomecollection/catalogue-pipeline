"""Normaliser for platform#6586: every work in production against the same work in the FOLIO pipeline.

Used at works-identified and works-denormalised. A Sierra work and the FOLIO work
replacing it go through the round 1 rules. Other works are compared as they are,
apart from the rules below.
"""

import runpy
from pathlib import Path

ROUND_1 = Path(__file__).parent / "6586_folio_sierra_identified_normalise.py"
round_1_normalise = runpy.run_path(str(ROUND_1))["normalise"]

# Identifiers of the source record itself, which the source swap changes.
RECORD_SCHEMES = {"sierra-identifier", "folio-instance", "folio-instance-hrid"}


def source_type(work: dict) -> str:
    return work["state"]["sourceIdentifier"]["identifierType"]["id"]


def outcome(work: dict) -> dict:
    """Return the type of a work and the work it redirects to."""
    result = {"type": work["type"]}
    if work.get("redirectTarget"):
        result["redirectTarget"] = work["redirectTarget"]["canonicalId"]
    return result


def fill_types(work: dict) -> None:
    """Store the default type where a pipeline left it out."""
    data = work.get("data") or {}
    for subject in data.get("subjects") or []:
        subject.setdefault("type", "Subject")
    for production in data.get("production") or []:
        for place in production.get("places") or []:
            place.setdefault("type", "Place")
        for date in production.get("dates") or []:
            date.setdefault("type", "Period")
        if production.get("function"):
            production["function"].setdefault("type", "Concept")


def normalise(prod: dict, folio: dict) -> tuple[dict, dict] | None:
    # Two works of different types, or two redirects, are compared on type and target.
    if prod["type"] != folio["type"] or prod["type"] == "Redirected":
        return outcome(prod), outcome(folio)

    for work in (prod, folio):
        sources = work.get("redirectSources") or []
        work["redirectSources"] = sorted(source["canonicalId"] for source in sources)
        fill_types(work)
        if "data" not in work:
            continue
        data = work["data"]
        data["otherIdentifiers"] = [
            identifier
            for identifier in data.get("otherIdentifiers") or []
            if identifier["identifierType"]["id"] not in RECORD_SCHEMES
        ]

    if source_type(prod) == "sierra-system-number" and source_type(folio) == "folio-instance":
        return round_1_normalise(prod, folio)
    return prod, folio
