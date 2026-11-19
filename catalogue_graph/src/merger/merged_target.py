"""Apply the field rules to a merge target."""

from collections.abc import Sequence
from dataclasses import dataclass

from ingestor.models.augmented.image import ParentWork
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
    """An image record to write, and the work it belongs to."""

    image: ImageData
    source: ParentWork


@dataclass(frozen=True)
class MergedTarget:
    """The target with its fields merged, the sources the rules claimed, and the image
    records to write."""

    target: VisibleIdentifiedWork
    claimed: list[IdentifiedWork]
    images: list[ImageWithSource]


def merge_fields(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedTarget:
    """Merge every field of the target from its sources."""
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
    return MergedTarget(
        target=merged_target,
        claimed=distinct_works(s for field in fields for s in field.claimed),
        images=_attributed_to(merged_target, separate_images.value),
    )


def merge_lone_target(target: VisibleIdentifiedWork) -> MergedTarget:
    """Merge a target that has no sources. The items rule still applies, and a
    single-image Miro work still emits its image."""
    items = merge_items(target, []).value
    data = target.data.model_copy(update={"items": items})
    merged_target = with_items_in_internal_works(
        target.model_copy(update={"data": data}), items
    )
    images = target.data.image_data if single_digital_item_miro_work(target) else []
    return MergedTarget(
        target=merged_target,
        claimed=[],
        images=_attributed_to(merged_target, images),
    )


def parent_work(work: VisibleIdentifiedWork) -> ParentWork:
    """The work an image belongs to. Its image data is omitted to keep image documents
    small."""
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
