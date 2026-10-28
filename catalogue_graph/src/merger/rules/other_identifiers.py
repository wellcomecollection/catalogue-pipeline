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
    distinct_works,
    first_defined,
)
from merger.rules.predicates import (
    Predicate,
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
from merger.rules.support import find_first_linked_digitised_lms_work
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
    seen: set[tuple[str, str, str]] = set()
    distinct = []
    for identifier in identifiers:
        key = (
            identifier.identifier_type.id,
            identifier.ontology_type,
            identifier.value,
        )
        if key not in seen:
            seen.add(key)
            distinct.append(identifier)
    return distinct


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
    def __init__(
        self, is_defined_for_target: Predicate, is_defined_for_source: Predicate
    ):
        self.is_defined_for_target = is_defined_for_target
        self.is_defined_for_source = is_defined_for_source

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        appended = [i for source in sources for i in allowed_identifiers(source)]
        return target.data.other_identifiers + appended


class MergeSingleMiroIntoSingleOrZeroItemLmsTarget(AppendAllowedIdentifiers):
    def __init__(self) -> None:
        super().__init__(
            (single_item_lms_work | zero_item_lms_work)
            & lms_work_picture_digital_image_or_3d_object,
            single_digital_item_miro_work,
        )

    def is_defined_for_source_list(self, sources: Sequence[IdentifiedWork]) -> bool:
        return (
            sum(1 for source in sources if single_digital_item_miro_work(source)) == 1
        )


class MergeDigitalIntoPhysicalLmsTarget(PartialRule[list[SourceIdentifier]]):
    """The physical bib takes every identifier of its digitised pair.

    Audiovisual pairs are not merged, as their bibs often hold different data.
    """

    is_defined_for_target = physical_lms_work & ~is_audiovisual
    is_defined_for_source = lms_work

    def rule(
        self, target: VisibleIdentifiedWork, sources: list[IdentifiedWork]
    ) -> list[SourceIdentifier]:
        digitised = find_first_linked_digitised_lms_work(target, sources)
        if digitised is None:
            logger.warning(
                "Unable to find other digitised LMS identifiers for the target",
                target=target.state.canonical_id,
            )
            return target.data.other_identifiers
        return [
            *target.data.other_identifiers,
            digitised.state.source_identifier,
            *digitised.data.other_identifiers,
        ]


merge_into_ebsco_target = AppendAllowedIdentifiers(ebsco_work, lms_work)
merge_into_tei_target = AppendAllowedIdentifiers(
    tei_work,
    lms_work | single_digital_item_miro_work | single_physical_item_cms_work,
)
merge_into_cms_target = AppendAllowedIdentifiers(
    single_physical_item_cms_work,
    single_digital_item_mets_work | lms_work | single_digital_item_miro_work,
)
merge_single_miro_into_lms_target = MergeSingleMiroIntoSingleOrZeroItemLmsTarget()
merge_miro_into_digmiro_target = AppendAllowedIdentifiers(
    lms_work_digitised_miro, single_digital_item_miro_work
)
merge_digital_into_physical_lms_target = MergeDigitalIntoPhysicalLmsTarget()


class OtherIdentifiersRule(FieldMergeRule[list[SourceIdentifier]]):
    def merge(
        self, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[SourceIdentifier]]:
        # The EBSCO rule claims its sources without changing the target's identifiers.
        contributing = [
            merge_into_tei_target,
            merge_into_cms_target,
            merge_single_miro_into_lms_target,
            merge_miro_into_digmiro_target,
        ]
        from_pair = merge_digital_into_physical_lms_target(target, sources)
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

        digitised = find_first_linked_digitised_lms_work(target, sources)
        merged_sources = distinct_works(
            [
                *(
                    s
                    for rule in [merge_into_ebsco_target, *contributing]
                    for s in rule.merged_sources(target, sources)
                ),
                *([digitised] if digitised is not None else []),
            ]
        )
        return FieldMergeResult(data=identifiers, sources=merged_sources)


other_identifiers_rule = OtherIdentifiersRule()
