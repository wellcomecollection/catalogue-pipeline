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
    FieldMergeResult,
    FieldMergeRule,
    PartialRule,
    SingleSource,
    distinct_works,
    first_defined,
    sources_used_by,
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

# The identifier schemes a source's other identifiers may carry onto the target.
# Digcodes are kept because they are used as a tagging and classification system.
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
    """The source's own identifier plus those of its other identifiers in an allowed scheme."""
    allowed = ALLOWED_SCHEMES
    if source.state.source_identifier.identifier_type.id == schemes.FOLIO_INSTANCE.id:
        allowed = ALLOWED_SCHEMES | FOLIO_ALLOWED_SCHEMES
    others = [
        identifier
        for identifier in source.data.other_identifiers
        if identifier.identifier_type.id in allowed
    ]
    return distinct_identifiers([source.state.source_identifier, *others])


class AppendAllowedIdentifiers(PartialRule[list[SourceIdentifier]]):
    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        appended = [i for source in sources for i in allowed_identifiers(source)]
        return target.data.other_identifiers + appended


class MergeIntoEbscoTarget(AppendAllowedIdentifiers):
    accepts_target = ebsco_work
    accepts_source = lms_work


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
    """The physical bib takes every identifier of its digitised pair."""

    accepts_target = physical_lms_work & ~is_audiovisual

    @classmethod
    def rule(
        cls, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        (digitised,) = sources
        return [
            *target.data.other_identifiers,
            digitised.state.source_identifier,
            *digitised.data.other_identifiers,
        ]


class OtherIdentifiersRule(FieldMergeRule[list[SourceIdentifier]]):
    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[SourceIdentifier]]:
        # The EBSCO rule claims its sources without changing the target's identifiers.
        contributing = [
            MergeIntoTeiTarget,
            MergeIntoCmsTarget,
            MergeSingleMiroIntoSingleOrZeroItemLmsTarget,
            MergeMiroIntoDigmiroTarget,
        ]
        from_pair = MergeDigitalIntoPhysicalLmsTarget.apply(target, sources)
        from_sources = first_defined(contributing, target, sources)
        # Both halves start from the target's own identifiers; the distinct step
        # removes the repeat when both apply.
        combined = [
            *(from_pair or []),
            *(from_sources or []),
        ]
        if from_pair is None and from_sources is None:
            combined = target.data.other_identifiers
        identifiers = distinct_identifiers(combined)

        claiming = [
            MergeIntoEbscoTarget,
            *contributing,
            MergeDigitalIntoPhysicalLmsTarget,
        ]
        used = sources_used_by(claiming, target, sources)
        return FieldMergeResult(data=identifiers, sources=distinct_works(used))
