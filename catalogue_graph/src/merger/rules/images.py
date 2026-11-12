"""The images of a merge: those kept on the target, and those emitted as image records.

Images are merged onto the LMS participant of the group, whether or not it is the
target of the merge, because it holds the information that decides which images go
where. The result is still applied to the actual target.
"""

from collections.abc import Callable, Sequence

from merger.rules.base import MergedField, Rule, claimed_by
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


class AllImages(Rule[list[ImageData]]):
    """Every image of the target and the claimed sources."""

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[ImageData]:
        return [image for work in [target, *sources] for image in work.data.image_data]


class PairedMiroImages(AllImages):
    """Miro images of a re-digitised work are superseded by the METS images."""

    accepts_target = lms_work & ~lms_work_digitised_miro
    accepts_source = single_digital_item_miro_work


class MetsPictureAndEphemeraImages(AllImages):
    """Only Pictures and Ephemera emit METS images.

    Other formats such as Books have far too many images for the image search.
    """

    accepts_target = lms_work_picture_or_ephemera
    accepts_source = single_digital_item_mets_work


type ImageMerge = Callable[
    [VisibleIdentifiedWork, Sequence[IdentifiedWork]], MergedField[list[ImageData]]
]


def merge_image_data(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    """The images kept on the merged work: Miro only, as METS images are reached via its items."""
    return _on_lms_participant(target, sources, _image_data)


def merge_images(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    """The images emitted as records: METS images, and Miro images with the METS licence."""
    return _on_lms_participant(target, sources, _images)


def _on_lms_participant(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork], merge: ImageMerge
) -> MergedField[list[ImageData]]:
    participants = [
        target,
        *(s for s in sources if isinstance(s, VisibleIdentifiedWork)),
    ]
    lms = choose_target(participants, satisfying=lms_work_digitised_miro) or (
        choose_target(participants, satisfying=lms_work)
    )
    return merge(lms, sources) if lms else MergedField([], [])


def _image_data(
    lms: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    return PairedMiroImages.apply(lms, sources) or MergedField([], [])


def _images(
    lms: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    rules: list[type[Rule[list[ImageData]]]] = [
        MetsPictureAndEphemeraImages,
        PairedMiroImages,
    ]
    mets = MetsPictureAndEphemeraImages.apply(lms, sources)
    mets_images = mets.value if mets else []
    miro = PairedMiroImages.apply(lms, sources)
    miro_images = [
        copy_licence_from(image, mets_images) for image in (miro.value if miro else [])
    ]
    return MergedField(mets_images + miro_images, claimed_by(rules, lms, sources))
