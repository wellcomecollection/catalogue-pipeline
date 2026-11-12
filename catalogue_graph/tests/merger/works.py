"""Builders for identified works in the shapes the transformers produce."""

import itertools
from collections.abc import Sequence

from ingestor.models.shared.deleted_reason import DeletedFromSource
from ingestor.models.shared.invisible_reason import InvisibleReason
from merger.rules.predicates import PHYSICAL_DIGITAL_REASON
from models import identifier_schemes as schemes
from models.pipeline.access_condition import AccessCondition
from models.pipeline.format import Format
from models.pipeline.id_label import Id
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedMergeCandidate,
    IdentifiedWorkState,
    InternalWorkStub,
    InvisibleIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified, SourceIdentifier, Unidentifiable
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import (
    ClosedStores,
    DigitalLocation,
    LocationType,
    OnlineResource,
    PhysicalLocation,
)
from models.pipeline.work_data import WorkData

EBSCO_SIERRA_REASON = "Ebsco/Sierra work"
MIRO_SIERRA_REASON = "Miro/Sierra work"
METS_REASON = "METS work"
AXIELL_SIERRA_REASON = "CALM/Sierra harvest work"
TEI_BNUMBER_REASON = "Bnumber present in TEI file"

Maps = Format(id="e", label="Maps")

CC_BY = Id(id="cc-by")
CC_BY_NC = Id(id="cc-by-nc")
IN_COPYRIGHT = Id(id="in-copyright")

IIIFImage = LocationType(id="iiif-image")
IIIFPresentation = LocationType(id="iiif-presentation")
ThumbnailImage = LocationType(id="thumbnail-image")

_counter = itertools.count(1)


def _next(prefix: str) -> str:
    return f"{prefix}{next(_counter):04d}"


def source_identifier(
    scheme: schemes.IdentifierScheme, value: str | None = None
) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=scheme.id),
        ontology_type="Work",
        value=value or _next(scheme.id[:2]),
    )


def digcode_identifier(digcode: str) -> SourceIdentifier:
    return source_identifier(schemes.WELLCOME_DIGCODE, digcode)


def physical_location(
    access_conditions: list[AccessCondition] | None = None,
) -> PhysicalLocation:
    return PhysicalLocation(
        location_type=ClosedStores,
        label="Closed stores",
        access_conditions=access_conditions or [],
    )


def digital_location(
    location_type: LocationType = IIIFPresentation,
    license: Id | None = CC_BY,
    access_conditions: list[AccessCondition] | None = None,
) -> DigitalLocation:
    return DigitalLocation(
        location_type=location_type,
        url=f"https://example.org/{_next('loc')}",
        license=license,
        access_conditions=access_conditions or [],
    )


def online_location(
    access_conditions: list[AccessCondition] | None = None,
) -> DigitalLocation:
    return digital_location(OnlineResource, access_conditions=access_conditions)


def physical_item(
    identified: bool = True, locations: list[PhysicalLocation] | None = None
) -> Item:
    item_id: Identified | Unidentifiable = Unidentifiable()
    if identified:
        item_id = Identified(
            canonical_id=_next("item"),
            source_identifier=source_identifier(schemes.SIERRA_IDENTIFIER),
        )
    return Item(id=item_id, locations=[*(locations or [physical_location()])])


def digital_item(locations: list[DigitalLocation] | None = None) -> Item:
    return Item(id=Unidentifiable(), locations=[*(locations or [digital_location()])])


def miro_item() -> Item:
    return digital_item([digital_location(IIIFImage)])


def image_data(locations: list[DigitalLocation] | None = None) -> ImageData:
    return ImageData(
        id=Identified(
            canonical_id=_next("image"),
            source_identifier=source_identifier(schemes.MIRO_IMAGE_NUMBER),
        ),
        version=1,
        locations=locations or [digital_location(IIIFImage)],
    )


def mets_image_data(license: Id | None = CC_BY) -> ImageData:
    return image_data(
        [
            digital_location(IIIFImage, license=license),
            digital_location(IIIFPresentation, license=license),
        ]
    )


def merge_candidate(
    target: VisibleIdentifiedWork, reason: str
) -> IdentifiedMergeCandidate:
    return IdentifiedMergeCandidate(
        id=Identified(
            canonical_id=target.state.canonical_id,
            source_identifier=target.state.source_identifier,
        ),
        reason=reason,
    )


def internal_work_stub() -> InternalWorkStub:
    return InternalWorkStub(
        source_identifier=source_identifier(schemes.TEI_MANUSCRIPT_ID),
        canonical_id=_next("inner"),
        work_data=WorkData(title=_next("tei-inner-")),
    )


def identified_work(
    scheme: schemes.IdentifierScheme,
    title: str | None = None,
    items: list[Item] | None = None,
    format: Format | None = None,
    other_identifiers: list[SourceIdentifier] | None = None,
    thumbnail: DigitalLocation | None = None,
    images: list[ImageData] | None = None,
    merge_candidates: list[IdentifiedMergeCandidate] | None = None,
    internal_work_stubs: list[InternalWorkStub] | None = None,
    removed_internal_work_stubs: list[InternalWorkStub] | None = None,
    redirect_sources: list[Identified] | None = None,
) -> VisibleIdentifiedWork:
    return VisibleIdentifiedWork(
        version=1,
        data=WorkData(
            title=title or _next("Title "),
            items=items or [],
            format=format,
            other_identifiers=other_identifiers or [],
            thumbnail=thumbnail,
            image_data=images or [],
        ),
        state=IdentifiedWorkState(
            source_identifier=source_identifier(scheme),
            canonical_id=_next("work"),
            source_modified_time="2026-01-01T00:00:00Z",
            merge_candidates=merge_candidates or [],
            internal_work_stubs=internal_work_stubs or [],
            removed_internal_work_stubs=removed_internal_work_stubs or [],
        ),
        redirect_sources=redirect_sources or [],
    )


def other_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A work from a source no rule merges."""
    return identified_work(schemes.NLM_MESH, **kwargs)  # type: ignore[arg-type]


def sierra_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A Sierra work carries its b-number and i-number as other identifiers."""
    kwargs.setdefault(
        "other_identifiers",
        [
            source_identifier(schemes.SIERRA_SYSTEM_NUMBER),
            source_identifier(schemes.SIERRA_IDENTIFIER),
        ],
    )
    return identified_work(schemes.SIERRA_SYSTEM_NUMBER, **kwargs)  # type: ignore[arg-type]


def sierra_physical_work(**kwargs: object) -> VisibleIdentifiedWork:
    return sierra_work(items=[physical_item()], **kwargs)


def sierra_digital_work(**kwargs: object) -> VisibleIdentifiedWork:
    return sierra_work(items=[digital_item()], **kwargs)


def sierra_pair() -> tuple[VisibleIdentifiedWork, VisibleIdentifiedWork]:
    """A digitised Sierra work and the physical one that links to it."""
    digitised = sierra_digital_work()
    physical = sierra_physical_work(
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)]
    )
    return digitised, physical


def sierra_ebsco_pair() -> tuple[VisibleIdentifiedWork, VisibleIdentifiedWork]:
    """A digital Sierra work and the EBSCO work it links to."""
    ebsco = ebsco_work()
    sierra = sierra_digital_work(
        merge_candidates=[merge_candidate(ebsco, EBSCO_SIERRA_REASON)]
    )
    return sierra, ebsco


def folio_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.FOLIO_INSTANCE, **kwargs)  # type: ignore[arg-type]


def ebsco_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.EBSCO_ALT_LOOKUP, **kwargs)  # type: ignore[arg-type]


def miro_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A Miro work: one image item, a thumbnail and one image."""
    kwargs.setdefault("items", [miro_item()])
    kwargs.setdefault("thumbnail", digital_location(ThumbnailImage))
    kwargs.setdefault("images", [image_data()])
    return identified_work(schemes.MIRO_IMAGE_NUMBER, **kwargs)  # type: ignore[arg-type]


def mets_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A METS work: one digital item and one image."""
    kwargs.setdefault("items", [digital_item()])
    kwargs.setdefault("images", [mets_image_data()])
    return identified_work(schemes.METS, **kwargs)  # type: ignore[arg-type]


def invisible_mets_work(
    num_images: int, license: Id | None = CC_BY
) -> InvisibleIdentifiedWork:
    return invisible(
        mets_work(images=[mets_image_data(license) for _ in range(num_images)])
    )


def calm_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A Calm work: one stub item in closed stores."""
    kwargs.setdefault("items", [physical_item(identified=False)])
    return identified_work(schemes.CALM_RECORD_ID, **kwargs)  # type: ignore[arg-type]


def axiell_work(**kwargs: object) -> VisibleIdentifiedWork:
    """An Axiell work: one stub item in closed stores."""
    kwargs.setdefault("items", [physical_item(identified=False)])
    return identified_work(schemes.AXIELL_GUID, **kwargs)  # type: ignore[arg-type]


def tei_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.TEI_MANUSCRIPT_ID, **kwargs)  # type: ignore[arg-type]


def invisible(work: VisibleIdentifiedWork) -> InvisibleIdentifiedWork:
    return InvisibleIdentifiedWork(
        version=work.version,
        data=work.data,
        state=work.state,
        invisibility_reasons=[InvisibleReason(type="UnableToTransform")],
    )


def deleted(work: VisibleIdentifiedWork) -> DeletedIdentifiedWork:
    return DeletedIdentifiedWork(
        version=work.version, state=work.state, deleted_reason=DeletedFromSource()
    )


def same_elements(actual: Sequence[object], expected: Sequence[object]) -> bool:
    """Equal as multisets, for values that are not hashable."""
    remaining = list(expected)
    for value in actual:
        if value not in remaining:
            return False
        remaining.remove(value)
    return not remaining
