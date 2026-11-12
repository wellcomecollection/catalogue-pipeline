"""Thumbnails come from a METS work first, else the Miro work with the smallest id.

If any digital location on the target or a source is restricted, no thumbnail is
emitted, so that nothing is shown that should not be. Physical restrictions are
about the physical item, e.g. its fragility, rather than its content, and are ignored.
"""

from collections.abc import Sequence

from merger.rules.base import MergedField, Rule, claimed_by, first_applicable
from merger.rules.predicates import (
    lms_work,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
    single_item_lms_work,
    single_physical_item_cms_work,
    tei_work,
    zero_item_lms_work,
)
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.location import DigitalLocation


class MetsThumbnail(Rule[DigitalLocation | None]):
    accepts_target = lms_work | single_physical_item_cms_work | tei_work
    accepts_source = single_digital_item_mets_work

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> DigitalLocation | None:
        return sources[0].data.thumbnail


class MinMiroThumbnail(Rule[DigitalLocation | None]):
    accepts_target = (
        single_item_lms_work
        | zero_item_lms_work
        | single_physical_item_cms_work
        | tei_work
    )
    accepts_source = single_digital_item_miro_work

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> DigitalLocation | None:
        first = min(sources, key=lambda source: source.state.source_identifier.value)
        return first.data.thumbnail


def _has_restricted_digital_location(work: IdentifiedWork) -> bool:
    return any(
        isinstance(location, DigitalLocation) and location.has_restrictions
        for item in work.data.items
        for location in item.locations
    )


def merge_thumbnail(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[DigitalLocation | None]:
    rules: list[type[Rule[DigitalLocation | None]]] = [MetsThumbnail, MinMiroThumbnail]
    claimed = claimed_by(rules, target, sources)
    if any(_has_restricted_digital_location(w) for w in [target, *sources]):
        return MergedField(None, claimed)
    # A rule that applies decides the thumbnail even when its source has none.
    applied = first_applicable(rules, target, sources)
    return MergedField(applied.value if applied else target.data.thumbnail, claimed)
