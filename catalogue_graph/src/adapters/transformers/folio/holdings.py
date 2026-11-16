"""Build work-level holdings from a FOLIO instance's holdings records (platform#6591, platform#6611)."""

from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedHoldings,
    FolioEnrichedInstance,
)
from adapters.transformers.folio.locations import (
    BOUND_WITH_CODES,
    ResolvedLocation,
    resolve_location,
)
from models.pipeline.holdings import Holdings
from models.pipeline.location import PhysicalLocation

# Links come only from the instance 856 (platform#6612), so Online holdings give nothing.
ONLINE_LOCATION_CODE = "elro"

# Holdings that put a bound volume on other linked instances, not this instance's own copies.
BOUND_WITH_HOLDINGS_TYPE = "Boundwiths"


def extract_holdings(instance: FolioEnrichedInstance) -> list[Holdings]:
    visible = [h for h in instance.holdings if not h.suppress_from_discovery]
    holdings: list[Holdings] = []
    for record in visible:
        enumeration = _enumeration(record)
        if not enumeration or _location_code(record) == ONLINE_LOCATION_CODE:
            continue
        built = Holdings(enumeration=enumeration, location=_location(record, visible))
        # Holdings carry no identifier, so identical ones are indistinguishable.
        if built not in holdings:
            holdings.append(built)
    return holdings


def _enumeration(record: FolioEnrichedHoldings) -> list[str]:
    # The migration joins several statements into one with ";" (platform#6800).
    return [
        part.strip()
        for s in record.holdings_statements
        if s.statement
        for part in s.statement.split(";")
        if part.strip()
    ]


def _location_code(record: FolioEnrichedHoldings) -> str | None:
    return record.effective_location.code if record.effective_location else None


def _resolve(record: FolioEnrichedHoldings) -> ResolvedLocation | None:
    if _location_code(record) in BOUND_WITH_CODES:
        return None
    return resolve_location(record.effective_location, holdings_id=record.id)


def _location(
    record: FolioEnrichedHoldings, visible: list[FolioEnrichedHoldings]
) -> PhysicalLocation | None:
    if (own := _resolve(record)) is not None:
        return _physical_location(own, record.call_number)

    # Most check-in holdings sit in the migration placeholder, while the
    # instance's items are on another holdings record in the real location.
    others = [
        resolved
        for other in visible
        if other is not record
        and other.holdings_type != BOUND_WITH_HOLDINGS_TYPE
        and (resolved := _resolve(other)) is not None
    ]
    if len({(r.location_type.id, r.label) for r in others}) != 1:
        # None to take, or several and no way to tell which the statements describe.
        return None
    # Their call number is the items' shelfmark, which Sierra never put on holdings.
    return _physical_location(others[0], record.call_number)


def _physical_location(
    resolved: ResolvedLocation, call_number: str | None
) -> PhysicalLocation:
    return PhysicalLocation(
        location_type=resolved.location_type,
        label=resolved.label,
        shelfmark=call_number,
        access_conditions=[],
    )
