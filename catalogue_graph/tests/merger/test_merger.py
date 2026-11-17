"""The merger redirects every source the field merge claimed and leaves the rest untouched."""

from collections.abc import Sequence

import pytest

from merger import merge as merge_module
from merger.merge import MergedComponent, merge_component
from merger.merged_target import MergedTarget
from models import identifier_schemes as schemes
from models.pipeline.identified.work import (
    IdentifiedWork,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified
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


def first_work(works: Sequence[IdentifiedWork]) -> VisibleIdentifiedWork | None:
    first = works[0]
    return first if isinstance(first, VisibleIdentifiedWork) else None


def copy_fields(
    target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
) -> MergedTarget:
    """Items claim the second source, other identifiers the third onwards."""
    data = target.data.model_copy(
        update={
            "items": merged_target_items,
            "other_identifiers": merged_other_identifiers,
        }
    )
    merged = target.model_copy(update={"data": data})
    return MergedTarget(merged, claimed=[sources[1], *sources[2:]], images=[])


@pytest.fixture(autouse=True)
def merge_into_the_first_work(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(merge_module, "first_by_precedence", first_work)
    monkeypatch.setattr(merge_module, "merge_fields", copy_fields)


@pytest.fixture
def outcome() -> MergedComponent:
    return merge_component(input_works)


def identified(work: IdentifiedWork) -> Identified:
    return Identified(
        canonical_id=work.state.canonical_id,
        source_identifier=work.state.source_identifier,
    )


def visible(outcome: MergedComponent, work: IdentifiedWork) -> VisibleIdentifiedWork:
    return next(
        w
        for w in outcome.works
        if isinstance(w, VisibleIdentifiedWork)
        and w.state.source_identifier == work.state.source_identifier
    )


def test_the_target_has_the_merged_fields(outcome: MergedComponent) -> None:
    merged = visible(outcome, input_works[0])

    assert merged.data == input_works[0].data.model_copy(
        update={
            "items": merged_target_items,
            "other_identifiers": merged_other_identifiers,
        }
    )


def test_the_target_lists_its_redirect_sources(outcome: MergedComponent) -> None:
    merged = visible(outcome, input_works[0])

    assert same_elements(
        merged.redirect_sources, [identified(w) for w in input_works[2:]]
    )


def test_every_source_claimed_by_a_rule_is_redirected(outcome: MergedComponent) -> None:
    redirected = [
        w.state.source_identifier
        for w in outcome.works
        if isinstance(w, RedirectedIdentifiedWork)
    ]

    assert same_elements(
        redirected, [w.state.source_identifier for w in input_works[2:]]
    )


def test_an_unclaimed_source_is_left_untouched(outcome: MergedComponent) -> None:
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

    merged = visible(merge_component([target, *sources]), target)

    assert all(i in merged.redirect_sources for i in existing)
    assert all(identified(s) in merged.redirect_sources for s in sources[1:])


def test_a_single_work_is_merged_on_its_own() -> None:
    work = identified_work(schemes.SIERRA_SYSTEM_NUMBER)

    assert merge_component([work]).works == [work]
