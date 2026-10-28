"""Predicates over identified works, from which the merge rules are built.

Several describe the shape the transformers give works from a source, e.g. a METS
work with a single digital item, and the rules rely on that shape. Predicates
combine with `&`, `|` and `~`.
"""

from collections.abc import Callable

from models import identifier_schemes as schemes
from models.pipeline.format import (
    AUDIOVISUAL_FORMAT_IDS,
    ArchivesDigital,
    DigitalImages,
    Ephemera,
    Format,
    Pictures,
    ThreeDObjects,
)
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.identifier import Identified
from models.pipeline.location import DigitalLocation, PhysicalLocation

PHYSICAL_DIGITAL_REASON = "Physical/digitised Sierra work"


class Predicate:
    def __init__(self, test: Callable[[IdentifiedWork], bool]):
        self._test = test

    def __call__(self, work: IdentifiedWork) -> bool:
        return self._test(work)

    def __and__(self, other: "Predicate") -> "Predicate":
        return Predicate(lambda work: self(work) and other(work))

    def __or__(self, other: "Predicate") -> "Predicate":
        return Predicate(lambda work: self(work) or other(work))

    def __invert__(self) -> "Predicate":
        return Predicate(lambda work: not self(work))


def source(scheme: schemes.IdentifierScheme) -> Predicate:
    return Predicate(
        lambda work: work.state.source_identifier.identifier_type.id == scheme.id
    )


def has_format(*formats: Format) -> Predicate:
    format_ids = {f.id for f in formats}
    return Predicate(
        lambda work: work.data.format is not None and work.data.format.id in format_ids
    )


def has_digcode(digcode: str) -> Predicate:
    return Predicate(
        lambda work: any(
            identifier.identifier_type.id == schemes.WELLCOME_DIGCODE.id
            and identifier.value == digcode
            for identifier in work.data.other_identifiers
        )
    )


def has_merge_reason(reason: str) -> Predicate:
    return Predicate(
        lambda work: any(
            reason in candidate.reason for candidate in work.state.merge_candidates
        )
    )


@Predicate
def any_work(work: IdentifiedWork) -> bool:
    return True


@Predicate
def is_visible(work: IdentifiedWork) -> bool:
    return isinstance(work, VisibleIdentifiedWork)


@Predicate
def zero_item(work: IdentifiedWork) -> bool:
    return not work.data.items


@Predicate
def single_item(work: IdentifiedWork) -> bool:
    return len(work.data.items) == 1


@Predicate
def multi_item(work: IdentifiedWork) -> bool:
    return len(work.data.items) > 1


@Predicate
def zero_identified_items(work: IdentifiedWork) -> bool:
    return not any(isinstance(item.id, Identified) for item in work.data.items)


@Predicate
def single_location(work: IdentifiedWork) -> bool:
    return all(len(item.locations) == 1 for item in work.data.items)


@Predicate
def physical_location_exists(work: IdentifiedWork) -> bool:
    return any(
        isinstance(location, PhysicalLocation)
        for item in work.data.items
        for location in item.locations
    )


@Predicate
def all_physical_locations(work: IdentifiedWork) -> bool:
    return all(
        isinstance(location, PhysicalLocation)
        for item in work.data.items
        for location in item.locations
    )


@Predicate
def all_digital_locations(work: IdentifiedWork) -> bool:
    return all(
        isinstance(location, DigitalLocation)
        for item in work.data.items
        for location in item.locations
    )


@Predicate
def is_audiovisual(work: IdentifiedWork) -> bool:
    return (
        work.data.format is not None and work.data.format.id in AUDIOVISUAL_FORMAT_IDS
    )


ebsco_work = source(schemes.EBSCO_ALT_LOOKUP)
miro_work = source(schemes.MIRO_IMAGE_NUMBER)
# A library management system work. Sierra and FOLIO run in separate pipelines,
# so the rules can treat them alike.
lms_work = source(schemes.SIERRA_SYSTEM_NUMBER) | source(schemes.FOLIO_INSTANCE)
# A collections management system work. Axiell replaced CALM and both give works
# the same shape, so the rules treat them alike.
cms_work = source(schemes.CALM_RECORD_ID) | source(schemes.AXIELL_GUID)

tei_work = source(schemes.TEI_MANUSCRIPT_ID) & zero_item & is_visible
single_physical_item_cms_work = (
    cms_work & single_item & single_location & all_physical_locations
)
# Born-digital material has no physical copy, so its CMS stub item is misleading.
born_digital_archive_stub_work = single_physical_item_cms_work & has_format(
    ArchivesDigital
)
single_digital_item_mets_work = (
    source(schemes.METS) & single_item & all_digital_locations
)
single_digital_item_miro_work = miro_work & single_item & all_digital_locations

zero_item_lms_work = lms_work & zero_item
single_item_lms_work = lms_work & single_item
multi_item_lms_work = lms_work & multi_item
physical_lms_work = lms_work & physical_location_exists
lms_work_picture_or_ephemera = lms_work & has_format(Pictures, Ephemera)
lms_work_picture_digital_image_or_3d_object = lms_work & has_format(
    DigitalImages, ThreeDObjects, Pictures
)

# AIDS posters were re-digitised and marked `digaids`; later re-digitised Miro works
# are marked `digmiro`.
lms_work_digitised_miro = lms_work & (has_digcode("digaids") | has_digcode("digmiro"))
# Sierra AV bibs may carry unidentified items drawn from 856 links; those don't count.
lms_work_digitised_av = lms_work & is_audiovisual & zero_identified_items

physical_digital = has_merge_reason(PHYSICAL_DIGITAL_REASON)
