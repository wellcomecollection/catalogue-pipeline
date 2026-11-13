"""Applying the field rules to a merge target, and turning what comes out into the
merged works and initial images the merger writes."""

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

from inferrer.models import InitialImage
from ingestor.models.augmented.image import ParentWork
from ingestor.models.merged.work import MergedWork
from merger.outputs import to_initial_image, to_merged_work
from merger.rules.base import distinct_works
from merger.rules.images import merge_image_data, merge_images
from merger.rules.items import merge_items
from merger.rules.other_identifiers import merge_other_identifiers
from merger.rules.predicates import single_digital_item_miro_work
from merger.rules.support import with_items_in_internal_works
from merger.rules.thumbnail import merge_thumbnail
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.identifier import Identified
from models.pipeline.image import ImageData


@dataclass(frozen=True)
class ImageWithSource:
    """An image to emit, and the work it is attributed to."""

    image: ImageData
    source: ParentWork


@dataclass(frozen=True)
class MergeResult:
    """The target with its fields merged, and the images created along the way."""

    merged_target: VisibleIdentifiedWork
    images: list[ImageWithSource]


def merge_fields(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> tuple[list[IdentifiedWork], MergeResult]:
    """The sources the field rules drew on, which are redirected to the target, and the result."""
    items = merge_items(target, sources)
    thumbnail = merge_thumbnail(target, sources)
    other_identifiers = merge_other_identifiers(target, sources)
    target_images = merge_image_data(target, sources)
    separate_images = merge_images(target, sources)

    data = target.data.model_copy(
        update={
            "items": items.value,
            "thumbnail": thumbnail.value,
            "other_identifiers": other_identifiers.value,
            "image_data": target_images.value,
        }
    )
    merged_target = with_items_in_internal_works(
        target.model_copy(update={"data": data}), items.value
    )
    fields = [items, thumbnail, other_identifiers, target_images, separate_images]
    claimed = distinct_works(source for field in fields for source in field.claimed)
    return claimed, MergeResult(
        merged_target=merged_target,
        images=_attributed_to(merged_target, separate_images.value),
    )


def target_only_merge_result(target: VisibleIdentifiedWork) -> MergeResult:
    """A target with no sources. Its items still go through the items rule, and a
    single-image Miro work emits its image."""
    items = merge_items(target, []).value
    data = target.data.model_copy(update={"items": items})
    merged_target = with_items_in_internal_works(
        target.model_copy(update={"data": data}), items
    )
    images = target.data.image_data if single_digital_item_miro_work(target) else []
    return MergeResult(
        merged_target=merged_target, images=_attributed_to(merged_target, images)
    )


def parent_work(work: VisibleIdentifiedWork) -> ParentWork:
    """The work an image is attributed to. Its own images are left out, as they would
    bloat every image document."""
    return ParentWork(
        id=Identified(
            canonical_id=work.state.canonical_id,
            source_identifier=work.state.source_identifier,
        ),
        data=work.data.model_copy(update={"image_data": []}),
        version=work.version,
    )


def _attributed_to(
    work: VisibleIdentifiedWork, images: Sequence[ImageData]
) -> list[ImageWithSource]:
    parent = parent_work(work)
    return [ImageWithSource(image=image, source=parent) for image in images]


@dataclass(frozen=True)
class MergerOutcome:
    """Everything a merge produces: the merged target, redirects, untouched works and
    internal works, plus the images, all stamped with one merged time on the way out."""

    works: list[IdentifiedWork]
    images: list[ImageWithSource]

    @staticmethod
    def pass_through(works: Sequence[IdentifiedWork]) -> "MergerOutcome":
        return MergerOutcome(works=list(works), images=[])

    def merged_works(self, merged_time: datetime) -> list[MergedWork]:
        return [to_merged_work(work, merged_time) for work in self.works]

    def initial_images(self, merged_time: datetime) -> list[InitialImage]:
        return [
            to_initial_image(image.image, image.source, merged_time)
            for image in self.images
        ]
