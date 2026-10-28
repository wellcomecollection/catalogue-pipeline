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
    sources_used_by,
)
from merger.rules.predicates import (
    lms_work,
    lms_work_digitised_miro,
    lms_work_picture_or_ephemera,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
)
from merger.rules.support import copy_licence_from
from merger.rules.target_precedence import choose_target
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.image import ImageData


class FlatImageMergeRule(PartialRule[list[ImageData]]):
    """Every image of the target and the matching sources."""

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[ImageData]:
        return [image for work in [target, *sources] for image in work.data.image_data]


class PairedMiroImages(FlatImageMergeRule):
    """Miro images of a re-digitised work are superseded by the METS images."""

    accepts_target = lms_work & ~lms_work_digitised_miro
    accepts_source = single_digital_item_miro_work


class MetsPictureAndEphemeraImages(FlatImageMergeRule):
    """Only Pictures and Ephemera emit METS images.

    Other formats such as Books have far too many images for the image search.
    """

    accepts_target = lms_work_picture_or_ephemera
    accepts_source = single_digital_item_mets_work


class ImageRule(FieldMergeRule[list[ImageData]]):
    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        participants = [
            target,
            *(s for s in sources if isinstance(s, VisibleIdentifiedWork)),
        ]
        lms_target = choose_target(
            participants, satisfying=lms_work_digitised_miro
        ) or choose_target(participants, satisfying=lms_work)
        if lms_target is None:
            return FieldMergeResult(data=[], sources=[])
        return cls.merge_lms_work_images(lms_target, sources)

    @classmethod
    @abstractmethod
    def merge_lms_work_images(
        cls, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]: ...


class ImageDataRule(ImageRule):
    """The images kept on the merged work: Miro only, as METS images are reached via its items."""

    @classmethod
    def merge_lms_work_images(
        cls, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        used = sources_used_by([PairedMiroImages], lms_target, sources)
        return FieldMergeResult(
            data=PairedMiroImages.apply(lms_target, sources) or [],
            sources=distinct_works(used),
        )


class ImagesRule(ImageRule):
    """The images emitted as records: METS images, and Miro images with the METS licence."""

    @classmethod
    def merge_lms_work_images(
        cls, lms_target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[ImageData]]:
        mets_images = MetsPictureAndEphemeraImages.apply(lms_target, sources) or []
        miro_images = [
            copy_licence_from(image, mets_images)
            for image in PairedMiroImages.apply(lms_target, sources) or []
        ]
        used = sources_used_by(
            [MetsPictureAndEphemeraImages, PairedMiroImages], lms_target, sources
        )
        return FieldMergeResult(
            data=mets_images + miro_images, sources=distinct_works(used)
        )
