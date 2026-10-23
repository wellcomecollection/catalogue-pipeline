"""FOLIO item location type and label, per docs/folio/location-mapping.md (platform#6584)."""

from collections.abc import Iterable
from dataclasses import dataclass

import structlog

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedItem
from ingestor.models.display.location_type import LOCATION_LABEL_MAPPING
from models.pipeline.location import (
    ClosedStores,
    LocationType,
    OnExhibition,
    OpenShelves,
)

logger = structlog.get_logger(__name__)

# Typed by library, so a location added to one of these libraries needs no change here.
LIBRARY_LOCATION_TYPES: dict[str, LocationType] = {
    "Closed stores": ClosedStores,
    "Offsite (DS)": ClosedStores,
    "Open shelves": OpenShelves,
    "On Exhibition": OnExhibition,
}

# Exceptions to the library rule, keyed on code because several locations were renamed.
CODE_LOCATION_TYPES: dict[str, LocationType] = {
    "DIGITISATION": ClosedStores,
    "axce": ClosedStores,
    "axcc": ClosedStores,
    "hicon": ClosedStores,
}
NO_LOCATION_CODES = frozenset({"none", "migration", "elro", "ILL", "ACQL", "enhal"})

# "Bound in above" and "contained in above": typed from the instance's other items.
BOUND_WITH_CODES = frozenset({"bwith", "cwith"})


@dataclass(frozen=True)
class ResolvedLocation:
    location_type: LocationType
    label: str


def is_bound_with(item: FolioEnrichedItem) -> bool:
    location = item.effective_location
    return location is not None and location.code in BOUND_WITH_CODES


def resolve_own_location(item: FolioEnrichedItem) -> ResolvedLocation | None:
    """The type and label of an item's effective location."""
    location = item.effective_location
    if location is None or location.code in NO_LOCATION_CODES:
        return None

    location_type = CODE_LOCATION_TYPES.get(
        location.code
    ) or LIBRARY_LOCATION_TYPES.get(location.library_name or "")
    if location_type is None:
        logger.warning(
            "Unmapped FOLIO location",
            item_id=item.id,
            location_code=location.code,
            library=location.library_name,
        )
        return None

    if location_type == ClosedStores:
        return ResolvedLocation(location_type, LOCATION_LABEL_MAPPING[ClosedStores.id])
    if location.name is None:
        logger.warning(
            "FOLIO location has no name", item_id=item.id, location_code=location.code
        )
        return None
    return ResolvedLocation(location_type, location.name)


def bound_with_location(
    own_locations: Iterable[ResolvedLocation | None],
) -> ResolvedLocation | None:
    """The one location shared by the instance's other items, as Sierra does per bib.

    Items without a location are ignored; if the rest disagree there is no location.
    """
    # Distinct host locations; keyed by value because LocationType isn't hashable.
    candidates = {
        (loc.location_type.id, loc.label): loc
        for loc in own_locations
        if loc is not None
    }
    if len(candidates) == 1:
        return next(iter(candidates.values()))
    if candidates:
        logger.warning(
            "Bound-with item has ambiguous host locations",
            locations=sorted(candidates),
        )
    return None


def resolve_locations(
    items: list[FolioEnrichedItem],
) -> dict[str, ResolvedLocation | None]:
    """Resolve every item's location by item id, applying the bound-with fallback."""
    bound_with = [i for i in items if is_bound_with(i)]
    own = {i.id: resolve_own_location(i) for i in items if not is_bound_with(i)}
    host = bound_with_location(own.values()) if bound_with else None
    return own | {i.id: host for i in bound_with}
