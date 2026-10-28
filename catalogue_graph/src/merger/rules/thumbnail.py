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
    is_defined_for_target = lms_work | single_physical_item_cms_work | tei_work
    is_defined_for_source = single_digital_item_mets_work

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> DigitalLocation | None:
        return sources[0].data.thumbnail


class MinMiroThumbnail(PartialRule[DigitalLocation | None]):
    is_defined_for_target = (
        single_item_lms_work
        | zero_item_lms_work
        | single_physical_item_cms_work
        | tei_work
    )
    is_defined_for_source = single_digital_item_miro_work

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> DigitalLocation | None:
        first = min(sources, key=lambda source: source.state.source_identifier.value)
        return first.data.thumbnail


mets_thumbnail = MetsThumbnail()
min_miro_thumbnail = MinMiroThumbnail()


def _has_restricted_digital_location(work: IdentifiedWork) -> bool:
    return any(
        isinstance(location, DigitalLocation) and location.has_restrictions
        for item in work.data.items
        for location in item.locations
    )


class ThumbnailRule(FieldMergeRule[DigitalLocation | None]):
    def merge(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[DigitalLocation | None]:
        return FieldMergeResult(
            data=self._thumbnail(target, sources),
            sources=distinct_works(
                [
                    *mets_thumbnail.merged_sources(target, sources),
                    *min_miro_thumbnail.merged_sources(target, sources),
                ]
            ),
        )

    @staticmethod
    def _thumbnail(
        target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> DigitalLocation | None:
        if any(_has_restricted_digital_location(w) for w in [target, *sources]):
            return None
        # A rule that applies decides the thumbnail even when its source has none.
        for rule in (mets_thumbnail, min_miro_thumbnail):
            merged = rule.merged_sources(target, sources)
            if merged:
                return rule.rule(target, merged)
        return target.data.thumbnail


thumbnail_rule = ThumbnailRule()
