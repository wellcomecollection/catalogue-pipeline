"""Identifiers are merged as follows.

* All source identifiers are merged into CMS and TEI works.
* Miro identifiers are merged into single or zero item LMS works, and into
  re-digitised LMS works.
* A physical LMS work takes the identifiers of its digitised pair.
* METS identifiers are not merged, as they are not useful.
"""

from collections.abc import Sequence

import structlog

from merger.rules.base import (
    MergedField,
    Rule,
    SingleSource,
    claimed_by,
    first_applicable,
)
from merger.rules.predicates import (
    ebsco_work,
    is_audiovisual,
    lms_work,
    lms_work_digitised_miro,
    lms_work_picture_digital_image_or_3d_object,
    physical_lms_work,
    single_digital_item_mets_work,
    single_digital_item_miro_work,
    single_item_lms_work,
    single_physical_item_cms_work,
    tei_work,
    zero_item_lms_work,
)
from merger.rules.support import DigitisedPairSource
from models import identifier_schemes as schemes
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.identifier import SourceIdentifier

logger = structlog.get_logger(__name__)

# A merged source's own identifier is always added to the target. Of the source's
# other identifiers, only those in these schemes are added. Digcodes are allowed
# because they are used as a tagging and classification system.
ALLOWED_SCHEMES = {
    schemes.WELLCOME_DIGCODE.id,
    schemes.SIERRA_IDENTIFIER.id,
    schemes.CALM_REF_NO.id,
    schemes.CALM_ALTREF_NO.id,
}
# A FOLIO work is identified by its instance UUID, so its HRID and Sierra b-number
# are other identifiers and have to be allowed explicitly.
FOLIO_ALLOWED_SCHEMES = {
    schemes.FOLIO_INSTANCE_HRID.id,
    schemes.SIERRA_SYSTEM_NUMBER.id,
}


def distinct_identifiers(
    identifiers: Sequence[SourceIdentifier],
) -> list[SourceIdentifier]:
    return list({str(identifier): identifier for identifier in identifiers}.values())


def allowed_identifiers(source: IdentifiedWork) -> list[SourceIdentifier]:
    """The source's own identifier, plus those of its other identifiers in an allowed
    scheme."""
    allowed = ALLOWED_SCHEMES
    if source.state.source_identifier.identifier_type.id == schemes.FOLIO_INSTANCE.id:
        allowed = ALLOWED_SCHEMES | FOLIO_ALLOWED_SCHEMES
    others = [
        identifier
        for identifier in source.data.other_identifiers
        if identifier.identifier_type.id in allowed
    ]
    return distinct_identifiers([source.state.source_identifier, *others])


class AppendAllowedIdentifiers(Rule[list[SourceIdentifier]]):
    """The identifiers the sources add to the target's own."""

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        return [i for source in sources for i in allowed_identifiers(source)]


class MergeIntoEbscoTarget(Rule[list[SourceIdentifier]]):
    """An e-bib merged into an EBSCO work is redirected to it, but none of its
    identifiers are added."""

    accepts_target = ebsco_work
    accepts_source = lms_work

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        return []


class MergeIntoTeiTarget(AppendAllowedIdentifiers):
    accepts_target = tei_work
    accepts_source = (
        lms_work | single_digital_item_miro_work | single_physical_item_cms_work
    )


class MergeIntoCmsTarget(AppendAllowedIdentifiers):
    accepts_target = single_physical_item_cms_work
    accepts_source = (
        single_digital_item_mets_work | lms_work | single_digital_item_miro_work
    )


class MergeSingleMiroIntoSingleOrZeroItemLmsTarget(
    SingleSource[list[SourceIdentifier]], AppendAllowedIdentifiers
):
    accepts_target = (
        single_item_lms_work | zero_item_lms_work
    ) & lms_work_picture_digital_image_or_3d_object
    accepts_source = single_digital_item_miro_work


class MergeMiroIntoDigmiroTarget(AppendAllowedIdentifiers):
    accepts_target = lms_work_digitised_miro
    accepts_source = single_digital_item_miro_work


class MergeDigitalIntoPhysicalLmsTarget(DigitisedPairSource[list[SourceIdentifier]]):
    """The physical bib adds all of its digitised bib's identifiers, not only the
    allowed schemes. Unlike a Miro or METS work, whose identifiers identify a
    photograph or a scan of the book, the digitised bib is a catalogue record of the
    book itself, so its identifiers all identify the book."""

    accepts_target = physical_lms_work & ~is_audiovisual

    @classmethod
    def value(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        (digitised,) = sources
        return [digitised.state.source_identifier, *digitised.data.other_identifiers]


def merge_other_identifiers(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedField[list[SourceIdentifier]]:
    rules: list[type[Rule[list[SourceIdentifier]]]] = [
        MergeIntoEbscoTarget,
        MergeIntoTeiTarget,
        MergeIntoCmsTarget,
        MergeSingleMiroIntoSingleOrZeroItemLmsTarget,
        MergeMiroIntoDigmiroTarget,
    ]
    # A physical bib with a digitised pair can also match one of the rules above, so
    # the pair rule is applied as well as, not instead of, the first that applies.
    from_pair = MergeDigitalIntoPhysicalLmsTarget.apply(target, sources)
    from_sources = first_applicable(rules, target, sources)
    identifiers = distinct_identifiers(
        [
            *target.data.other_identifiers,
            *(from_pair.value if from_pair else []),
            *(from_sources.value if from_sources else []),
        ]
    )
    claimed = claimed_by([*rules, MergeDigitalIntoPhysicalLmsTarget], target, sources)
    return MergedField(identifiers, claimed)
