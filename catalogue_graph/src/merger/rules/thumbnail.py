"""Thumbnails come from a METS work first, else the Miro work with the smallest id.

If any digital location on the target or a source is restricted, no thumbnail is
emitted, so that nothing is shown that should not be. Physical restrictions are
about the physical item, e.g. its fragility, rather than its content, and are ignored.
"""

from collections.abc import Sequence

from merger.rules.base import (
    FieldMergeResult,
    FieldMergeRule,
    PartialRule,
    distinct_works,
    sources_used_by,
)
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


class MetsThumbnail(PartialRule[DigitalLocation | None]):
    accepts_target = lms_work | single_physical_item_cms_work | tei_work
    accepts_source = single_digital_item_mets_work

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> DigitalLocation | None:
        return sources[0].data.thumbnail


class MinMiroThumbnail(PartialRule[DigitalLocation | None]):
    accepts_target = (
        single_item_lms_work
        | zero_item_lms_work
        | single_physical_item_cms_work
        | tei_work
    )
    accepts_source = single_digital_item_miro_work

    @classmethod
    def rule(
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


class ThumbnailRule(FieldMergeRule[DigitalLocation | None]):
    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[DigitalLocation | None]:
        used = sources_used_by([MetsThumbnail, MinMiroThumbnail], target, sources)
        return FieldMergeResult(
            data=cls._thumbnail(target, sources), sources=distinct_works(used)
        )

    @staticmethod
    def _thumbnail(
        target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> DigitalLocation | None:
        if any(_has_restricted_digital_location(w) for w in [target, *sources]):
            return None
        # A rule that applies decides the thumbnail even when its source has none.
        for rule in (MetsThumbnail, MinMiroThumbnail):
            merged = rule.merged_sources(target, sources)
            if merged:
                return rule.rule(target, merged)
        return target.data.thumbnail
