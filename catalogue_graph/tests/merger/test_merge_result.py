"""Applying the field rules to a target, and the merged works and images that result."""

from datetime import UTC, datetime

from merger.merge_result import (
    ImageWithSource,
    MergerOutcome,
    merge_fields,
    parent_work,
    target_only_merge_result,
)
from merger.outputs import to_initial_image, to_merged_work
from models.pipeline.format import ArchivesDigital, ThreeDObjects
from models.pipeline.identified.work import VisibleIdentifiedWork
from tests.merger.works import (
    TEI_BNUMBER_REASON,
    axiell_work,
    internal_work_stub,
    merge_candidate,
    miro_work,
    same_elements,
    sierra_physical_work,
    tei_work,
)

MERGED_AT = datetime(2026, 11, 11, 9, 30, tzinfo=UTC)


def test_a_sierra_3d_object_physical_work_merged_with_a_miro_work() -> None:
    sierra = sierra_physical_work(format=ThreeDObjects)
    miro = miro_work()

    redirected, result = merge_fields(sierra, [miro])

    assert redirected == [miro]
    merged = result.merged_target
    assert merged.state == sierra.state
    assert merged.redirect_sources == []
    assert merged.data.other_identifiers == [
        *sierra.data.other_identifiers,
        miro.state.source_identifier,
        *miro.data.other_identifiers,
    ]
    assert merged.data.thumbnail == miro.data.thumbnail
    [sierra_item] = sierra.data.items
    [miro_item] = miro.data.items
    assert merged.data.items == [
        sierra_item.model_copy(
            update={"locations": sierra_item.locations + miro_item.locations}
        )
    ]
    assert merged.data.image_data == miro.data.image_data
    assert result.images == [
        ImageWithSource(image=image, source=parent_work(merged))
        for image in miro.data.image_data
    ]


def test_the_items_merged_into_a_tei_work_reach_its_inner_works() -> None:
    sierra_works = [sierra_physical_work() for _ in range(3)]
    tei = tei_work(
        merge_candidates=[merge_candidate(w, TEI_BNUMBER_REASON) for w in sierra_works],
        internal_work_stubs=[internal_work_stub(), internal_work_stub()],
    )

    redirected, result = merge_fields(tei, sierra_works)

    assert same_elements(redirected, sierra_works)
    items = result.merged_target.data.items
    assert same_elements(items, [i for w in sierra_works for i in w.data.items])
    for stub in result.merged_target.state.internal_work_stubs:
        assert stub.work_data.items == items


def test_a_single_miro_target_emits_its_image() -> None:
    miro = miro_work()

    result = target_only_merge_result(miro)

    assert result.merged_target == miro
    assert result.images == [
        ImageWithSource(image=image, source=parent_work(miro))
        for image in miro.data.image_data
    ]


def test_a_born_digital_axiell_work_on_its_own_drops_its_stub_item() -> None:
    axiell = axiell_work(format=ArchivesDigital)

    result = target_only_merge_result(axiell)

    assert result.merged_target.data.items == []
    assert result.images == []


def test_the_parent_work_carries_everything_but_the_images() -> None:
    miro = miro_work()

    parent = parent_work(miro)

    assert parent.id.canonical_id == miro.state.canonical_id
    assert parent.id.source_identifier == miro.state.source_identifier
    assert parent.version == miro.version
    assert parent.data == miro.data.model_copy(update={"image_data": []})


def test_the_outcome_stamps_the_merged_time_on_works_and_images() -> None:
    miro = miro_work()
    result = target_only_merge_result(miro)
    outcome = MergerOutcome(works=[result.merged_target], images=result.images)

    assert outcome.merged_works(MERGED_AT) == [to_merged_work(miro, MERGED_AT)]
    assert outcome.initial_images(MERGED_AT) == [
        to_initial_image(image, parent_work(miro), MERGED_AT)
        for image in miro.data.image_data
    ]


def test_works_passed_through_are_merged_as_they_are() -> None:
    works: list[VisibleIdentifiedWork] = [sierra_physical_work(), miro_work()]

    outcome = MergerOutcome.pass_through(works)

    assert outcome.merged_works(MERGED_AT) == [
        to_merged_work(work, MERGED_AT) for work in works
    ]
    assert outcome.initial_images(MERGED_AT) == []
