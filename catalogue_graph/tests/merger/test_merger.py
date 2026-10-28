"""The merger applies its rules to a target and redirects the sources they claimed."""

from collections.abc import Sequence

from merger.merge import Merger, MergeResult, MergerOutcome, identified_id
from merger.rules.base import FieldMergeResult, FieldMergeRule
from models import identifier_schemes as schemes
from models.pipeline.identified.work import (
    IdentifiedWork,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified, SourceIdentifier
from models.pipeline.item import Item
from tests.merger.works import (
    digital_item,
    identified_work,
    invisible,
    mets_work,
    miro_work,
    same_elements,
    sierra_digital_work,
    source_identifier,
)

input_works: list[IdentifiedWork] = [
    *(sierra_digital_work() for _ in range(6)),
    *(miro_work() for _ in range(6)),
    *(invisible(mets_work()) for _ in range(6)),
]
merged_target_items = [digital_item() for _ in range(4)]
merged_other_identifiers = [
    source_identifier(schemes.SIERRA_SYSTEM_NUMBER) for _ in range(4)
]


class CopyItemsRule(FieldMergeRule[list[Item]]):
    """Claims the second source."""

    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[Item]]:
        return FieldMergeResult(
            data=merged_target_items, redirected_sources=[sources[1]]
        )


class CopyOtherIdentifiersRule(FieldMergeRule[list[SourceIdentifier]]):
    """Claims the third source onwards."""

    @classmethod
    def merge(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> FieldMergeResult[list[SourceIdentifier]]:
        return FieldMergeResult(
            data=merged_other_identifiers, redirected_sources=list(sources[2:])
        )


class FirstWorkMerger(Merger):
    """Merges everything into the first work."""

    @classmethod
    def find_target(
        cls, works: Sequence[IdentifiedWork]
    ) -> VisibleIdentifiedWork | None:
        first = works[0]
        return first if isinstance(first, VisibleIdentifiedWork) else None

    @classmethod
    def create_merge_result(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> tuple[list[IdentifiedWork], MergeResult]:
        items = CopyItemsRule.merge(target, sources)
        other_identifiers = CopyOtherIdentifiersRule.merge(target, sources)
        data = target.data.model_copy(
            update={"items": items.data, "other_identifiers": other_identifiers.data}
        )
        merged = target.model_copy(update={"data": data})
        return (
            items.redirected_sources + other_identifiers.redirected_sources,
            MergeResult(merged, []),
        )


outcome = FirstWorkMerger.merge(input_works)


def visible(outcome: MergerOutcome, work: IdentifiedWork) -> VisibleIdentifiedWork:
    return next(
        w
        for w in outcome.works
        if isinstance(w, VisibleIdentifiedWork)
        and w.state.source_identifier == work.state.source_identifier
    )


def test_the_target_has_the_merged_fields() -> None:
    merged = visible(outcome, input_works[0])

    assert merged.data == input_works[0].data.model_copy(
        update={
            "items": merged_target_items,
            "other_identifiers": merged_other_identifiers,
        }
    )


def test_the_target_lists_its_redirect_sources() -> None:
    merged = visible(outcome, input_works[0])

    assert same_elements(
        merged.redirect_sources, [identified_id(w) for w in input_works[2:]]
    )


def test_every_source_claimed_by_a_rule_is_redirected() -> None:
    redirected = [
        w.state.source_identifier
        for w in outcome.works
        if isinstance(w, RedirectedIdentifiedWork)
    ]

    assert same_elements(
        redirected, [w.state.source_identifier for w in input_works[2:]]
    )


def test_an_unclaimed_source_is_left_untouched() -> None:
    assert input_works[1] in outcome.works


def test_existing_redirect_sources_on_the_target_are_kept() -> None:
    existing = [
        Identified(
            canonical_id=f"existing{i}",
            source_identifier=source_identifier(schemes.SIERRA_SYSTEM_NUMBER),
        )
        for i in range(3)
    ]
    target = identified_work(schemes.SIERRA_SYSTEM_NUMBER, redirect_sources=existing)
    sources = [identified_work(schemes.SIERRA_SYSTEM_NUMBER) for _ in range(5)]

    merged = visible(FirstWorkMerger.merge([target, *sources]), target)

    assert all(i in merged.redirect_sources for i in existing)
    assert all(identified_id(s) in merged.redirect_sources for s in sources[1:])
