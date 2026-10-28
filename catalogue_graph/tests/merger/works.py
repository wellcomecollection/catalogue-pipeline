"""Builders for identified works in the shapes the transformers produce."""

import itertools

from ingestor.models.shared.deleted_reason import DeletedFromSource
from ingestor.models.shared.invisible_reason import InvisibleReason
from models import identifier_schemes as schemes
from models.pipeline.format import Format
from models.pipeline.id_label import Id
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedMergeCandidate,
    IdentifiedWorkState,
    InvisibleIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified, SourceIdentifier, Unidentifiable
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import (
    ClosedStores,
    DigitalLocation,
    OnlineResource,
    PhysicalLocation,
)
from models.pipeline.work_data import WorkData

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


def physical_location() -> PhysicalLocation:
    return PhysicalLocation(
        location_type=ClosedStores, label="Closed stores", access_conditions=[]
    )


def digital_location() -> DigitalLocation:
    return DigitalLocation(
        location_type=OnlineResource,
        url=f"https://example.org/{_next('loc')}",
        access_conditions=[],
    )


def physical_item(identified: bool = True) -> Item:
    item_id: Identified | Unidentifiable = Unidentifiable()
    if identified:
        item_id = Identified(
            canonical_id=_next("item"),
            source_identifier=source_identifier(schemes.SIERRA_IDENTIFIER),
        )
    return Item(id=item_id, locations=[physical_location()])


def digital_item() -> Item:
    return Item(id=Unidentifiable(), locations=[digital_location()])


def image_data() -> ImageData:
    return ImageData(
        id=Identified(
            canonical_id=_next("image"),
            source_identifier=source_identifier(schemes.MIRO_IMAGE_NUMBER),
        ),
        version=1,
        locations=[digital_location()],
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


def identified_work(
    scheme: schemes.IdentifierScheme,
    items: list[Item] | None = None,
    format: Format | None = None,
    other_identifiers: list[SourceIdentifier] | None = None,
    thumbnail: DigitalLocation | None = None,
    images: list[ImageData] | None = None,
    merge_candidates: list[IdentifiedMergeCandidate] | None = None,
) -> VisibleIdentifiedWork:
    return VisibleIdentifiedWork(
        version=1,
        data=WorkData(
            title=_next("Title "),
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
        ),
    )


def sierra_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.SIERRA_SYSTEM_NUMBER, **kwargs)  # type: ignore[arg-type]


def sierra_physical_work(**kwargs: object) -> VisibleIdentifiedWork:
    return sierra_work(items=[physical_item()], **kwargs)


def sierra_digital_work(**kwargs: object) -> VisibleIdentifiedWork:
    return sierra_work(items=[digital_item()], **kwargs)


def folio_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.FOLIO_INSTANCE, **kwargs)  # type: ignore[arg-type]


def ebsco_work(**kwargs: object) -> VisibleIdentifiedWork:
    return identified_work(schemes.EBSCO_ALT_LOOKUP, **kwargs)  # type: ignore[arg-type]


def miro_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A Miro work: one digital item, a thumbnail and one image."""
    kwargs.setdefault("items", [digital_item()])
    kwargs.setdefault("thumbnail", digital_location())
    kwargs.setdefault("images", [image_data()])
    return identified_work(schemes.MIRO_IMAGE_NUMBER, **kwargs)  # type: ignore[arg-type]


def mets_work(**kwargs: object) -> VisibleIdentifiedWork:
    """A METS work: one digital item, a thumbnail and one image."""
    kwargs.setdefault("items", [digital_item()])
    kwargs.setdefault("thumbnail", digital_location())
    kwargs.setdefault("images", [image_data()])
    return identified_work(schemes.METS, **kwargs)  # type: ignore[arg-type]


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
