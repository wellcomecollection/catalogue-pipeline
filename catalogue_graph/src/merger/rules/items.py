"""Items are merged as follows.

* LMS single or zero item: METS works, and single Miro works when the LMS work is a
  Picture, Digital Image or 3-D Object.
* LMS multi item: METS works.
* CMS and TEI: the items of every LMS, METS and Miro source replace the target's own.
* Physical LMS with a digitised pair: the pair's online items are appended.
"""

from collections.abc import Sequence

from merger.rules.base import (
    FieldMergeResult,
    FieldMergeRule,
    PartialRule,
    SingleSource,
    distinct_works,
    first_defined,
    sources_used_by,
)
from merger.rules.predicates import (
    born_digital_archive_stub_work,
    is_audiovisual,
    lms_work,
    lms_work_digitised_miro,
    lms_work_picture_digital_image_or_3d_object,
    physical_digital,
    physical_lms_work,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
    single_item_lms_work,
    single_physical_item_cms_work,
    tei_work,
    zero_item_lms_work,
)
from merger.rules.support import (
    DigitisedPairSource,
    append_locations_from,
    is_available_online,
)
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.item import Item


def _source_items(sources: Sequence[IdentifiedWork]) -> list[Item]:
    return [item for source in sources for item in source.data.items]


class MergeMetsIntoLmsTarget(PartialRule[list[Item]]):
    """A lone LMS item takes the METS locations; otherwise the METS items are appended."""

    accepts_target = lms_work
    accepts_source = single_digital_item_mets_work

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[Item]:
        if len(target.data.items) == 1:
            return [append_locations_from(target.data.items[0], sources)]
        return target.data.items + _source_items(sources)


class MergeSingleMiroIntoSingleOrZeroItemLmsTarget(SingleSource[list[Item]]):
    """A lone LMS item takes the Miro locations; with no LMS item, the Miro item is used.

    With several LMS items the Miro item belongs to one of them, but which is unknown,
    so nothing is merged rather than risk a duplicate.
    """

    accepts_target = (
        single_item_lms_work | zero_item_lms_work
    ) & lms_work_picture_digital_image_or_3d_object
    accepts_source = single_digital_item_miro_work

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[Item]:
        if len(target.data.items) == 1:
            return [append_locations_from(target.data.items[0], sources)]
        return _source_items(sources)


class ReplaceTargetItems(PartialRule[list[Item]]):
    """The sources' items replace the target's own.

    CMS items are stubs: the LMS item created when the record was harvested holds the
    access status and hold count, so it and any linked METS or Miro items are kept
    instead.
    """

    accepts_source = (
        single_digital_item_mets_work | single_digital_item_miro_work | lms_work
    )

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[Item]:
        return _source_items(sources)


class MergeIntoTeiTarget(ReplaceTargetItems):
    accepts_target = tei_work


class MergeIntoCmsTarget(ReplaceTargetItems):
    accepts_target = single_physical_item_cms_work


class MergeDigitalIntoPhysicalLmsTarget(DigitisedPairSource[list[Item]]):
    """The 856 items of the digitised pair are appended to the physical bib's."""

    accepts_target = physical_lms_work & physical_digital & ~is_audiovisual

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[Item]:
        (digitised,) = sources
        online = [item for item in digitised.data.items if is_available_online(item)]
        return target.data.items + online


def known_duplicate_sources(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> list[IdentifiedWork]:
    """Miro sources subsumed by a re-digitised LMS target without using their data.

    The Miro item of a re-digitised work is identical to its METS item, so the Miro
    work is wholly covered even though it cannot be merged into a multi-item target.
    """
    if lms_work_digitised_miro(target) and any(
        single_digital_item_mets_work(source) for source in sources
    ):
        return [source for source in sources if single_digital_item_miro_work(source)]
    return []


class ItemsRule(FieldMergeRule[list[Item]]):
    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[Item]]:
        contributing: list[type[PartialRule[list[Item]]]] = [
            MergeIntoTeiTarget,
            MergeIntoCmsTarget,
            MergeMetsIntoLmsTarget,
            MergeSingleMiroIntoSingleOrZeroItemLmsTarget,
            MergeDigitalIntoPhysicalLmsTarget,
        ]
        items = first_defined(contributing, target, sources)
        if items is None:
            items = cls._unmerged_items(target)

        used = sources_used_by(contributing, target, sources)
        used += known_duplicate_sources(target, sources)
        return FieldMergeResult(data=items, sources=distinct_works(used))

    @staticmethod
    def _unmerged_items(target: VisibleIdentifiedWork) -> list[Item]:
        """The target's own items when no source replaces them."""
        return [] if born_digital_archive_stub_work(target) else target.data.items
