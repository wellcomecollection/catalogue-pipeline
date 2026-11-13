"""The images of a merge: those kept on the target, and those emitted as image records.

Which images go where is decided by the LMS work in the group, whether or not it is
the target: its digcodes say whether its Miro images have been re-digitised, and its
format whether its METS images are wanted. The result is applied to the target.
"""

from collections.abc import Sequence

from merger.rules.base import MergedField, Rule, claimed_by
from merger.rules.precedence import first_by_precedence
from merger.rules.predicates import (
    Predicate,
    lms_work,
    lms_work_digitised_miro,
    lms_work_picture_or_ephemera,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
)
from merger.rules.support import copy_licence_from
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.image import ImageData


class AllImages(Rule[list[ImageData]]):
    """The images of the claimed sources, for a group whose LMS work passes the condition.

    The LMS work never carries images of its own: only METS and Miro works do.
    """

    accepts_lms_work: Predicate

    @classmethod
    def claims(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> list[IdentifiedWork]:
        # A re-digitised LMS work is looked for first, so that its digcode is seen
        # even when a physical one outranks it.
        participants = [
            target,
            *(s for s in sources if isinstance(s, VisibleIdentifiedWork)),
        ]
        lms = first_by_precedence(participants, satisfying=lms_work_digitised_miro) or (
            first_by_precedence(participants, satisfying=lms_work)
        )
        if lms is None or not cls.accepts_lms_work(lms):
            return []
        return [source for source in sources if cls.accepts_source(source)]

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[ImageData]:
        return [image for work in sources for image in work.data.image_data]


class PairedMiroImages(AllImages):
    """Miro images of a re-digitised work are superseded by the METS images."""

    accepts_lms_work = lms_work & ~lms_work_digitised_miro
    accepts_source = single_digital_item_miro_work


class MetsPictureAndEphemeraImages(AllImages):
    """Only Pictures and Ephemera emit METS images.

    Other formats such as Books have far too many images for the image search.
    """

    accepts_lms_work = lms_work_picture_or_ephemera
    accepts_source = single_digital_item_mets_work


def merge_image_data(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    """The images kept on the merged work: Miro only, as METS images are reached via its items."""
    return PairedMiroImages.apply(target, sources) or MergedField([], [])


def merge_images(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[ImageData]]:
    """The images emitted as records: METS images, and Miro images with the METS licence."""
    rules: list[type[Rule[list[ImageData]]]] = [
        MetsPictureAndEphemeraImages,
        PairedMiroImages,
    ]
    mets = MetsPictureAndEphemeraImages.apply(target, sources)
    mets_images = mets.value if mets else []
    miro = PairedMiroImages.apply(target, sources)
    miro_images = [
        copy_licence_from(image, mets_images) for image in (miro.value if miro else [])
    ]
    return MergedField(mets_images + miro_images, claimed_by(rules, target, sources))
