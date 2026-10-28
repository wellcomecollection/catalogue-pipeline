"""The images of a merge: those kept on the target, and those emitted as image records.

Images are merged onto the LMS participant of the group, whether or not it is the
target of the merge, because it holds the information that decides which images go
where. The result is still applied to the actual target.
"""

from abc import abstractmethod
from collections.abc import Sequence

from merger.rules.base import (
    FieldMergeResult,
    FieldMergeRule,
    PartialRule,
    distinct_works,
)
from merger.rules.predicates import (
    Predicate,
    lms_work,
    lms_work_digitised_miro,
    lms_work_picture_or_ephemera,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
)
from merger.rules.support import copy_licence_from
from merger.rules.target_precedence import target_satisfying
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.image import ImageData


class FlatImageMergeRule(PartialRule[list[ImageData]]):
    """Every image of the target and the matching sources."""

    def __init__(
        self, is_defined_for_target: Predicate, is_defined_for_source: Predicate
    ):
        self.is_defined_for_target = is_defined_for_target
        self.is_defined_for_source = is_defined_for_source

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[ImageData]:
        return [image for work in [target, *sources] for image in work.data.image_data]


# Miro images of a re-digitised work are superseded by the METS images.
paired_miro_images = FlatImageMergeRule(
    lms_work & ~lms_work_digitised_miro, single_digital_item_miro_work
)
# Only Pictures and Ephemera emit METS images; other formats such as Books have far
# too many images for the image search.
mets_picture_and_ephemera_images = FlatImageMergeRule(
    lms_work_picture_or_ephemera, single_digital_item_mets_work
)


class ImageRule(FieldMergeRule[list[ImageData]]):
    def merge(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        participants = [
            target,
            *(s for s in sources if isinstance(s, VisibleIdentifiedWork)),
        ]
        lms_target = target_satisfying(
            lms_work_digitised_miro, participants
        ) or target_satisfying(lms_work, participants)
        if lms_target is None:
            return FieldMergeResult(data=[], sources=[])
        return self.merge_lms_work_images(lms_target, sources)

    @abstractmethod
    def merge_lms_work_images(
        self, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]: ...


class ImageDataRule(ImageRule):
    """The images kept on the merged work: Miro only, as METS images are reached via its items."""

    def merge_lms_work_images(
        self, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        return FieldMergeResult(
            data=paired_miro_images(lms_target, sources) or [],
            sources=distinct_works(
                paired_miro_images.merged_sources(lms_target, sources)
            ),
        )


class ImagesRule(ImageRule):
    """The images emitted as records: METS images, and Miro images with the METS licence."""

    def merge_lms_work_images(
        self, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        mets_images = mets_picture_and_ephemera_images(lms_target, sources) or []
        miro_images = [
            copy_licence_from(image, mets_images)
            for image in paired_miro_images(lms_target, sources) or []
        ]
        return FieldMergeResult(
            data=mets_images + miro_images,
            sources=distinct_works(
                [
                    *mets_picture_and_ephemera_images.merged_sources(
                        lms_target, sources
                    ),
                    *paired_miro_images.merged_sources(lms_target, sources),
                ]
            ),
        )


image_data_rule = ImageDataRule()
images_rule = ImagesRule()
