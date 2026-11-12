"""Operations on works, items and images that several merge rules share."""

from collections.abc import Sequence

import structlog

from merger.rules.base import PartialRule
from merger.rules.predicates import (
    PHYSICAL_DIGITAL_REASON,
    all_digital_locations,
    is_audiovisual,
    lms_work,
    physical_digital,
    physical_lms_work,
)
from models.pipeline.id_label import Id
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import OnlineResource

logger = structlog.get_logger(__name__)


def find_first_linked_digitised_lms_work(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> IdentifiedWork | None:
    """The digitised bib paired with a physical LMS target, whichever side holds the link.

    Audiovisual works are catalogued as separate physical and digitised bibs that
    routinely differ, so neither is treated as canonical and they are never paired.
    See platform issue 4876.
    """
    if is_audiovisual(target) or not physical_lms_work(target):
        return None

    if physical_digital(target):
        return _linked_from_physical(target, sources)
    return _linked_from_digitised(target, sources)


def _linked_from_physical(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> IdentifiedWork | None:
    """The physical bib links to the e-bib, via a merge candidate on the target."""
    linked_ids = [
        candidate.id.canonical_id
        for candidate in target.state.merge_candidates
        if PHYSICAL_DIGITAL_REASON in candidate.reason
    ]
    sources_by_id = {
        source.state.canonical_id: source
        for source in sources
        if source.state.canonical_id in linked_ids
    }
    if len(sources_by_id) > 1 or len(linked_ids) > 1:
        logger.warning(
            "More than one candidate source for a physical/digital merge",
            expected=linked_ids,
            actual=list(sources_by_id),
        )

    linked = next((sources_by_id[i] for i in linked_ids if i in sources_by_id), None)
    if linked is None:
        logger.warning(
            "Could not find the digitised LMS work",
            target=target.state.canonical_id,
            expected=linked_ids,
            actual=list(sources_by_id),
        )
    return linked


def _linked_from_digitised(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> IdentifiedWork | None:
    """The e-bib links to the physical bib, but not the other way round."""
    for source in sources:
        if not (lms_work(source) and all_digital_locations(source)):
            continue
        for candidate in source.state.merge_candidates:
            if (
                candidate.reason == PHYSICAL_DIGITAL_REASON
                and candidate.id.canonical_id == target.state.canonical_id
            ):
                return source
    return None


class DigitisedPairSource[T](PartialRule[T]):
    """Merges the digitised pair of a physical LMS target, and nothing else.

    Audiovisual pairs are not merged, as their bibs often hold different data.
    """

    accepts_source = lms_work

    @classmethod
    def merged_sources(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        candidates = super().merged_sources(target, sources)
        digitised = find_first_linked_digitised_lms_work(target, candidates)
        return [digitised] if digitised is not None else []


def append_locations_from(item: Item, sources: Sequence[IdentifiedWork]) -> Item:
    locations = [
        location
        for source in sources
        for source_item in source.data.items
        for location in source_item.locations
    ]
    return item.model_copy(update={"locations": item.locations + locations})


def is_available_online(item: Item) -> bool:
    return any(
        location.location_type.id == OnlineResource.id for location in item.locations
    )


def copy_licence_from(image: ImageData, sources: list[ImageData]) -> ImageData:
    """The image with the sources' licence, when they agree on exactly one."""
    licences: list[Id] = []
    for source in sources:
        for location in source.locations:
            if location.license is not None and location.license not in licences:
                licences.append(location.license)

    if len(licences) != 1:
        if licences:
            logger.warning(
                "Multiple source licences present, leaving the original licence",
                licences=[licence.id for licence in licences],
            )
        return image

    locations = [
        location.model_copy(update={"license": licences[0]})
        for location in image.locations
    ]
    return image.model_copy(update={"locations": locations})


def with_items_in_internal_works(
    work: VisibleIdentifiedWork, items: list[Item]
) -> VisibleIdentifiedWork:
    """The work with every internal work given the items, so each can be requested directly."""
    stubs = [
        stub.model_copy(
            update={"work_data": stub.work_data.model_copy(update={"items": items})}
        )
        for stub in work.state.internal_work_stubs
    ]
    state = work.state.model_copy(update={"internal_work_stubs": stubs})
    return work.model_copy(update={"state": state})
