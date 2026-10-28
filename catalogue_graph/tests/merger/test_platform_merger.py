"""Merging groups of works from every source with the platform's rules."""

import json
from collections.abc import Sequence

import pytest

from merger.merge import (
    ImageWithParent,
    MergerOutcome,
    PlatformMerger,
    identified_id,
    parent_work,
)
from merger.rules.predicates import PHYSICAL_DIGITAL_REASON
from models import identifier_schemes as schemes
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import ViewOnline
from models.pipeline.access_status import LicensedResource
from models.pipeline.format import (
    ArchivesDigital,
    Audio,
    Books,
    DigitalImages,
    EVideos,
    Film,
    Format,
    Pictures,
    ThreeDObjects,
    Videos,
)
from models.pipeline.id_label import Id
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedWork,
    IdentifiedWorkState,
    InvisibleIdentifiedWork,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import SourceIdentifier, Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation, OnlineResource
from tests.merger.works import (
    AXIELL_SIERRA_REASON,
    CC_BY,
    CC_BY_NC,
    METS_REASON,
    MIRO_SIERRA_REASON,
    TEI_BNUMBER_REASON,
    ThumbnailImage,
    axiell_work,
    calm_work,
    deleted,
    digcode_identifier,
    digital_item,
    digital_location,
    folio_work,
    identified_work,
    internal_work_stub,
    invisible,
    merge_candidate,
    mets_image_data,
    mets_work,
    miro_work,
    other_work,
    physical_item,
    same_elements,
    sierra_digital_work,
    sierra_ebsco_pair,
    sierra_physical_work,
    sierra_work,
    source_identifier,
    tei_work,
)

merger = PlatformMerger

digital_location_cc_by_nc = digital_location(license=CC_BY_NC)
digital_location_no_license = digital_location_cc_by_nc.model_copy(
    update={"license": None}
)

sierra_digitised = sierra_digital_work()
sierra_physical = sierra_physical_work(
    format=ThreeDObjects,
    merge_candidates=[merge_candidate(sierra_digitised, PHYSICAL_DIGITAL_REASON)],
)
zero_item_sierra = sierra_work(format=Pictures)
multiple_items_sierra = sierra_work(
    items=[physical_item(), physical_item()],
    merge_candidates=[merge_candidate(sierra_digitised, PHYSICAL_DIGITAL_REASON)],
)
sierra_digital = sierra_work(
    items=[digital_item([digital_location_no_license])], format=DigitalImages
)
sierra_picture = sierra_work(items=[physical_item()], format=Pictures)
miro = miro_work()
mets = invisible(
    mets_work(
        items=[digital_item([digital_location_cc_by_nc])],
        images=[mets_image_data()],
        thumbnail=DigitalLocation(
            url="https://path.to/thumbnail.jpg",
            location_type=ThumbnailImage,
            license=CC_BY,
            access_conditions=[],
        ),
    )
)
calm = calm_work()


def identifiers(work: IdentifiedWork) -> list[SourceIdentifier]:
    return [work.state.source_identifier, *work.data.other_identifiers]


def visible_works(outcome: MergerOutcome) -> dict[str, VisibleIdentifiedWork]:
    return {
        w.state.canonical_id: w
        for w in outcome.works
        if isinstance(w, VisibleIdentifiedWork)
    }


def redirects(outcome: MergerOutcome) -> dict[str, str]:
    return {
        w.state.canonical_id: w.redirect_target.canonical_id
        for w in outcome.works
        if isinstance(w, RedirectedIdentifiedWork)
    }


def deleted_works(outcome: MergerOutcome) -> list[DeletedIdentifiedWork]:
    return [w for w in outcome.works if isinstance(w, DeletedIdentifiedWork)]


def invisible_works(outcome: MergerOutcome) -> list[InvisibleIdentifiedWork]:
    return [w for w in outcome.works if isinstance(w, InvisibleIdentifiedWork)]


def merged(
    target: VisibleIdentifiedWork,
    redirected: Sequence[IdentifiedWork],
    **data: object,
) -> VisibleIdentifiedWork:
    """The target as the merger emits it: changed fields, redirect sources."""
    return VisibleIdentifiedWork(
        version=target.version,
        data=target.data.model_copy(update=data),
        state=target.state,
        redirect_sources=[identified_id(w) for w in redirected],
    )


def redirect(
    source: IdentifiedWork, target: IdentifiedWork
) -> RedirectedIdentifiedWork:
    return RedirectedIdentifiedWork(
        version=source.version,
        state=IdentifiedWorkState(
            source_identifier=source.state.source_identifier,
            canonical_id=source.state.canonical_id,
            source_modified_time=source.state.source_modified_time,
            merge_candidates=source.state.merge_candidates,
        ),
        redirect_target=identified_id(target),
    )


def with_locations(item: Item, *sources: IdentifiedWork) -> Item:
    locations = [loc for s in sources for i in s.data.items for loc in i.locations]
    return item.model_copy(update={"locations": item.locations + locations})


@pytest.mark.parametrize(
    ("works", "target"),
    [
        ([sierra_digital, calm, sierra_physical, mets, miro], calm),
        ([sierra_digital, sierra_physical, mets, miro], sierra_physical),
        ([sierra_digital, mets, miro], sierra_digital),
        ([mets, miro], None),
    ],
    ids=["calm", "sierra with physical item", "sierra", "nothing"],
)
def test_the_target_is_calm_then_physical_sierra_then_sierra_then_nothing(
    works: list[IdentifiedWork], target: VisibleIdentifiedWork | None
) -> None:
    assert merger.find_target(works) == target


def test_a_physical_sierra_picture_or_3d_object_merges_with_a_miro_work() -> None:
    outcome = merger.merge([sierra_physical, miro])

    expected = merged(
        sierra_physical,
        [miro],
        other_identifiers=sierra_physical.data.other_identifiers + identifiers(miro),
        thumbnail=miro.data.thumbnail,
        items=[with_locations(sierra_physical.data.items[0], miro)],
        image_data=miro.data.image_data,
    )
    assert same_elements(outcome.works, [expected, redirect(miro, sierra_physical)])
    assert outcome.images == [
        ImageWithParent(miro.data.image_data[0], parent_work(expected))
    ]


def test_a_zero_item_sierra_work_merges_with_a_miro_work() -> None:
    outcome = merger.merge([zero_item_sierra, miro])

    expected = merged(
        zero_item_sierra,
        [miro],
        other_identifiers=zero_item_sierra.data.other_identifiers + identifiers(miro),
        thumbnail=miro.data.thumbnail,
        items=miro.data.items,
        image_data=miro.data.image_data,
    )
    assert same_elements(outcome.works, [expected, redirect(miro, zero_item_sierra)])
    assert outcome.images == [
        ImageWithParent(miro.data.image_data[0], parent_work(expected))
    ]


def test_a_digital_sierra_picture_or_3d_object_merges_with_a_miro_work() -> None:
    outcome = merger.merge([sierra_digital, miro])

    expected = merged(
        sierra_digital,
        [miro],
        other_identifiers=sierra_digital.data.other_identifiers + identifiers(miro),
        thumbnail=miro.data.thumbnail,
        items=[with_locations(sierra_digital.data.items[0], miro)],
        image_data=miro.data.image_data,
    )
    assert same_elements(outcome.works, [expected, redirect(miro, sierra_digital)])
    assert outcome.images == [
        ImageWithParent(miro.data.image_data[0], parent_work(expected))
    ]


def test_a_multi_item_sierra_work_takes_only_the_images_of_a_miro_work() -> None:
    outcome = merger.merge([multiple_items_sierra, miro])

    expected = merged(multiple_items_sierra, [miro], image_data=miro.data.image_data)
    assert same_elements(
        outcome.works, [redirect(miro, multiple_items_sierra), expected]
    )


class TestNonPictureSierraWorkWithMets:
    expected = merged(
        sierra_physical,
        [mets],
        items=[with_locations(sierra_physical.data.items[0], mets)],
        thumbnail=mets.data.thumbnail,
    )
    expected_redirect = redirect(mets, sierra_physical)

    def test_the_two_works_merge(self) -> None:
        outcome = merger.merge([sierra_physical, mets])

        assert same_elements(outcome.works, [self.expected, self.expected_redirect])
        assert outcome.images == []

    def test_a_deleted_work_is_ignored_when_merging_the_others(self) -> None:
        deleted_work = deleted(other_work())

        outcome = merger.merge([sierra_physical, mets, deleted_work])

        assert same_elements(
            outcome.works, [self.expected, self.expected_redirect, deleted_work]
        )
        assert outcome.images == []


def test_a_picture_sierra_work_merges_with_a_mets_work() -> None:
    outcome = merger.merge([sierra_picture, mets])

    expected = merged(
        sierra_picture,
        [mets],
        items=[with_locations(sierra_picture.data.items[0], mets)],
        image_data=[],
        thumbnail=mets.data.thumbnail,
    )
    assert same_elements(outcome.works, [expected, redirect(mets, sierra_picture)])
    assert outcome.images == [
        ImageWithParent(mets.data.image_data[0], parent_work(expected))
    ]


def test_a_physical_3d_object_merges_with_its_digitised_pair_a_miro_and_a_mets_work() -> (
    None
):
    outcome = merger.merge([sierra_physical, sierra_digitised, miro, mets])

    expected = merged(
        sierra_physical,
        [mets, miro, sierra_digitised],
        other_identifiers=sierra_physical.data.other_identifiers
        + identifiers(sierra_digitised)
        + identifiers(miro),
        thumbnail=mets.data.thumbnail,
        items=[with_locations(sierra_physical.data.items[0], mets)],
        image_data=miro.data.image_data,
    )
    works = visible_works(outcome)
    assert list(works) == [sierra_physical.state.canonical_id]
    assert works[sierra_physical.state.canonical_id].data == expected.data
    assert same_elements(
        works[sierra_physical.state.canonical_id].redirect_sources,
        expected.redirect_sources,
    )
    assert same_elements(
        [w for w in outcome.works if isinstance(w, RedirectedIdentifiedWork)],
        [
            redirect(sierra_digitised, sierra_physical),
            redirect(miro, sierra_physical),
            redirect(mets, sierra_physical),
        ],
    )
    assert outcome.images == [
        ImageWithParent(miro.data.image_data[0], parent_work(expected))
    ]


def test_a_multi_item_physical_sierra_work_merges_with_a_mets_work() -> None:
    outcome = merger.merge([multiple_items_sierra, mets])

    expected = merged(
        multiple_items_sierra,
        [mets],
        thumbnail=mets.data.thumbnail,
        items=multiple_items_sierra.data.items + mets.data.items,
    )
    assert same_elements(
        outcome.works, [expected, redirect(mets, multiple_items_sierra)]
    )
    assert outcome.images == []


def test_a_multi_item_physical_sierra_work_merges_with_its_pair_and_a_mets_work() -> (
    None
):
    outcome = merger.merge([multiple_items_sierra, sierra_digitised, mets])

    expected = merged(
        multiple_items_sierra,
        [mets, sierra_digitised],
        other_identifiers=multiple_items_sierra.data.other_identifiers
        + identifiers(sierra_digitised),
        thumbnail=mets.data.thumbnail,
        items=multiple_items_sierra.data.items + mets.data.items,
    )
    works = visible_works(outcome)
    assert works[multiple_items_sierra.state.canonical_id].data == expected.data
    assert same_elements(
        works[multiple_items_sierra.state.canonical_id].redirect_sources,
        expected.redirect_sources,
    )
    assert same_elements(
        [w for w in outcome.works if isinstance(w, RedirectedIdentifiedWork)],
        [
            redirect(sierra_digitised, multiple_items_sierra),
            redirect(mets, multiple_items_sierra),
        ],
    )
    assert outcome.images == []


def test_a_lone_miro_work_emits_its_image() -> None:
    outcome = merger.merge([miro])

    assert outcome.works == [miro]
    assert outcome.images == [
        ImageWithParent(miro.data.image_data[0], parent_work(miro))
    ]


def test_sierra_audiovisual_works_are_not_merged() -> None:
    digitised_video = sierra_digital_work(format=EVideos)
    physical_video = sierra_physical_work(
        format=Videos,
        merge_candidates=[merge_candidate(digitised_video, PHYSICAL_DIGITAL_REASON)],
    )

    outcome = merger.merge([digitised_video, physical_video])

    assert same_elements(outcome.works, [physical_video, digitised_video])


def test_both_works_are_unmodified_if_one_is_deleted() -> None:
    visible = other_work()
    deleted_work = deleted(other_work())

    outcome = merger.merge([visible, deleted_work])

    assert same_elements(outcome.works, [visible, deleted_work])


def test_digitised_videos_from_mets_merge_into_the_e_bib() -> None:
    # Based on a real example where the METS work was merged into the physical
    # video formats rather than the more detailed e-bib.
    physical_formats = sierra_work(
        title="A work with physical video formats, e.g. DVD or digibeta",
        format=Film,
        items=[physical_item()],
    )
    ebib = sierra_work(
        title="A work for an e-bib",
        format=Videos,
        merge_candidates=[merge_candidate(physical_formats, PHYSICAL_DIGITAL_REASON)],
    )
    mets_for_ebib = invisible(
        mets_work(
            title="A METS work",
            merge_candidates=[merge_candidate(ebib, METS_REASON)],
            items=[digital_item()],
            images=[],
        )
    )
    film_reel = sierra_work(
        title="Work for film reel",
        format=Videos,
        merge_candidates=[merge_candidate(ebib, PHYSICAL_DIGITAL_REASON)],
    )
    sierra_works = [physical_formats, ebib, film_reel]

    outcome = merger.merge([*sierra_works, mets_for_ebib])

    works = visible_works(outcome)
    assert same_elements(list(works), [w.state.canonical_id for w in sierra_works])
    assert redirects(outcome) == {
        mets_for_ebib.state.canonical_id: ebib.state.canonical_id
    }
    assert invisible_works(outcome) == []
    assert works[physical_formats.state.canonical_id].data.items == (
        physical_formats.data.items
    )
    assert works[film_reel.state.canonical_id].data.items == film_reel.data.items
    assert works[ebib.state.canonical_id].data.items == mets_for_ebib.data.items


def test_digitised_audio_from_mets_merges_into_the_e_bib() -> None:
    # Based on a real example where the METS work was merged into the physical
    # audio formats rather than the e-bib.
    cassette = sierra_work(
        title="A physical cassette tape", format=Audio, items=[physical_item()]
    )
    ebib = sierra_work(title="A work for an e-bib", format=Audio)
    mets_for_ebib = invisible(
        mets_work(
            title="The digital copy of the audio cassette",
            items=[digital_item()],
            images=[],
        )
    )

    outcome = merger.merge([cassette, ebib, mets_for_ebib])

    works = visible_works(outcome)
    assert same_elements(
        list(works), [cassette.state.canonical_id, ebib.state.canonical_id]
    )
    assert redirects(outcome) == {
        mets_for_ebib.state.canonical_id: ebib.state.canonical_id
    }
    assert invisible_works(outcome) == []
    assert works[cassette.state.canonical_id].data.items == cassette.data.items
    assert works[ebib.state.canonical_id].data.items == mets_for_ebib.data.items


def test_a_mets_work_naming_the_physical_bib_still_merges_into_the_e_bib() -> None:
    cassette = sierra_work(format=Audio, items=[physical_item()])
    ebib = sierra_work(
        format=Audio,
        merge_candidates=[merge_candidate(cassette, PHYSICAL_DIGITAL_REASON)],
    )
    mets_for_cassette = invisible(
        mets_work(
            merge_candidates=[merge_candidate(cassette, METS_REASON)],
            items=[digital_item()],
            images=[],
        )
    )

    outcome = merger.merge([cassette, ebib, mets_for_cassette])

    assert redirects(outcome) == {
        mets_for_cassette.state.canonical_id: ebib.state.canonical_id
    }


def av_ebibs_for(
    physical: VisibleIdentifiedWork, count: int = 2
) -> list[VisibleIdentifiedWork]:
    return [
        sierra_work(
            format=Audio,
            merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)],
        )
        for _ in range(count)
    ]


def mets_for(*works: VisibleIdentifiedWork) -> InvisibleIdentifiedWork:
    return invisible(
        mets_work(
            merge_candidates=[merge_candidate(w, METS_REASON) for w in works],
            items=[digital_item()],
            images=[],
        )
    )


def test_a_mets_work_naming_the_physical_bib_merges_into_it_once_the_e_bibs_are_carved_out() -> (
    None
):
    physical = sierra_work(format=Audio, items=[physical_item()])
    ebibs = av_ebibs_for(physical)
    mets_for_ebibs = [mets_for(ebib) for ebib in ebibs]
    mets_for_physical = mets_for(physical)

    outcome = merger.merge([physical, *ebibs, *mets_for_ebibs, mets_for_physical])

    assert redirects(outcome) == {
        mets_for_ebibs[0].state.canonical_id: ebibs[0].state.canonical_id,
        mets_for_ebibs[1].state.canonical_id: ebibs[1].state.canonical_id,
        mets_for_physical.state.canonical_id: physical.state.canonical_id,
    }
    locations = [
        loc
        for item in visible_works(outcome)[physical.state.canonical_id].data.items
        for loc in item.locations
    ]
    assert same_elements(
        locations,
        [
            loc
            for w in (physical, mets_for_physical)
            for item in w.data.items
            for loc in item.locations
        ],
    )


def test_a_mets_work_linked_to_several_av_e_bibs_goes_to_the_one_not_carved_out() -> (
    None
):
    physical = sierra_work(format=Audio, items=[physical_item()])
    ebibs = av_ebibs_for(physical)
    ambiguous_mets = mets_for(*ebibs)
    mets_for_first = mets_for(ebibs[0])

    outcome = merger.merge([physical, *ebibs, ambiguous_mets, mets_for_first])

    result = redirects(outcome)
    assert result[mets_for_first.state.canonical_id] == ebibs[0].state.canonical_id
    assert result[ambiguous_mets.state.canonical_id] == ebibs[1].state.canonical_id


def test_a_mets_work_linked_to_several_av_e_bibs_goes_to_the_physical_bib_once_every_e_bib_is_carved_out() -> (
    None
):
    physical = sierra_work(format=Audio, items=[physical_item()])
    ebibs = av_ebibs_for(physical)
    mets_for_ebibs = [mets_for(ebib) for ebib in ebibs]
    ambiguous_mets = mets_for(*ebibs)

    outcome = merger.merge([physical, *ebibs, *mets_for_ebibs, ambiguous_mets])

    assert redirects(outcome) == {
        mets_for_ebibs[0].state.canonical_id: ebibs[0].state.canonical_id,
        mets_for_ebibs[1].state.canonical_id: ebibs[1].state.canonical_id,
        ambiguous_mets.state.canonical_id: physical.state.canonical_id,
    }


# A two-sided cassette: one physical bib and two e-bibs, both linking to it via
# 776. Both METS works used to attach to whichever e-bib was elected target.
# See platform issue 6643.
cassette = sierra_work(
    title="A physical cassette tape", format=Audio, items=[physical_item()]
)
side_a = sierra_work(
    title="Side A",
    format=Audio,
    items=[digital_item()],
    merge_candidates=[merge_candidate(cassette, PHYSICAL_DIGITAL_REASON)],
)
side_b = sierra_work(
    title="Side B",
    format=Audio,
    merge_candidates=[merge_candidate(cassette, PHYSICAL_DIGITAL_REASON)],
)
mets_for_side_a = mets_for(side_a)
mets_for_side_b = mets_for(side_b)


@pytest.mark.parametrize(
    "works",
    [
        [cassette, side_a, side_b, mets_for_side_a, mets_for_side_b],
        [mets_for_side_b, side_b, side_a, mets_for_side_a, cassette],
    ],
    ids=["in order", "shuffled"],
)
def test_each_av_e_bib_gets_its_own_mets_work(works: list[IdentifiedWork]) -> None:
    outcome = merger.merge(works)

    visible = visible_works(outcome)
    assert same_elements(
        list(visible), [w.state.canonical_id for w in (cassette, side_a, side_b)]
    )
    assert redirects(outcome) == {
        mets_for_side_a.state.canonical_id: side_a.state.canonical_id,
        mets_for_side_b.state.canonical_id: side_b.state.canonical_id,
    }
    assert visible[cassette.state.canonical_id].data.items == cassette.data.items
    assert visible[side_a.state.canonical_id].data.items == [
        with_locations(side_a.data.items[0], mets_for_side_a)
    ]
    assert visible[side_b.state.canonical_id].data.items == mets_for_side_b.data.items


def online_resource_item() -> Item:
    return Item(
        id=Unidentifiable(),
        title="Scope: UK Disability Charity",
        locations=[
            DigitalLocation(
                url="http://www.scope.org.uk",
                location_type=OnlineResource,
                access_conditions=[
                    AccessCondition(method=ViewOnline, status=LicensedResource)
                ],
            )
        ],
    )


def test_online_resources_are_ignored_by_the_physical_digital_rules() -> None:
    # Based on a real example where the METS work was merged into the physical
    # video rather than the e-bib it belonged to.
    e_video = sierra_work(format=Videos, items=[online_resource_item()])
    physical_video = sierra_work(
        format=Videos,
        items=[physical_item()],
        merge_candidates=[merge_candidate(e_video, PHYSICAL_DIGITAL_REASON)],
    )
    mets_for_video = mets_work(merge_candidates=[merge_candidate(e_video, METS_REASON)])

    outcome = merger.merge([e_video, physical_video, mets_for_video])

    assert invisible_works(outcome) == []
    assert redirects(outcome) == {
        mets_for_video.state.canonical_id: e_video.state.canonical_id
    }


def test_the_856_web_link_item_is_kept_when_merging_a_physical_digitised_pair() -> None:
    # Based on a real example: links to digitised journals in the Internet Archive
    # live in the 856 field of the digitised record and must survive the merge.
    item = Item(
        id=Unidentifiable(),
        locations=[
            DigitalLocation(
                url="https://example.org/b12345678",
                location_type=OnlineResource,
                access_conditions=[
                    AccessCondition(method=ViewOnline, status=LicensedResource)
                ],
            )
        ],
    )
    digitised = sierra_work(items=[item])
    physical = sierra_work(
        items=[physical_item()],
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )

    outcome = merger.merge([digitised, physical])

    assert len(redirects(outcome)) == 1
    visible = list(visible_works(outcome).values())
    assert len(visible) == 1
    assert item in visible[0].data.items


def test_identifiers_are_kept_when_the_e_bib_holds_the_link() -> None:
    # Based on a real issue where identifiers were lost in the merge.
    physical = sierra_work(
        other_identifiers=[source_identifier(schemes.SIERRA_IDENTIFIER)],
        format=Books,
        items=[physical_item()],
    )
    electronic = sierra_work(
        other_identifiers=[
            source_identifier(schemes.SIERRA_IDENTIFIER),
            digcode_identifier("digsexology"),
        ],
        format=Books,
        merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)],
    )
    mets_for_electronic = invisible(
        mets_work(
            merge_candidates=[merge_candidate(electronic, METS_REASON)],
            items=[digital_item()],
        )
    )

    outcome = merger.merge([mets_for_electronic, electronic, physical])

    visible = list(visible_works(outcome).values())
    assert visible[0].state.canonical_id == physical.state.canonical_id
    assert same_elements(
        identifiers(visible[0]), identifiers(physical) + identifiers(electronic)
    )


def test_a_physical_digitised_pair_keeps_its_identifiers_with_an_unrelated_miro_work() -> (
    None
):
    # Regression test: e-bib -> physical bib -> Miro work lost its identifiers.
    unrelated_miro = miro_work()
    physical = sierra_physical_work(
        format=Books,
        merge_candidates=[merge_candidate(unrelated_miro, MIRO_SIERRA_REASON)],
    )
    digitised = sierra_digital_work(
        merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)]
    )

    outcome = merger.merge([unrelated_miro, physical, digitised])

    visible = list(visible_works(outcome).values())
    assert same_elements(
        identifiers(visible[0]), identifiers(physical) + identifiers(digitised)
    )


def test_a_digital_sierra_work_merges_into_an_ebsco_work() -> None:
    sierra, ebsco = sierra_ebsco_pair()

    outcome = merger.merge([sierra, ebsco])

    assert len(redirects(outcome)) == 1
    assert list(visible_works(outcome)) == [ebsco.state.canonical_id]


def test_a_physical_sierra_work_merges_into_a_tei_work() -> None:
    physical = sierra_work(items=[physical_item()])
    tei = tei_work(
        merge_candidates=[merge_candidate(physical, PHYSICAL_DIGITAL_REASON)]
    )

    outcome = merger.merge([tei, physical])

    assert len(redirects(outcome)) == 1
    assert list(visible_works(outcome)) == [tei.state.canonical_id]


def test_several_sierra_bibs_and_their_calm_records_merge_into_one_tei_work() -> None:
    # A manuscript can be catalogued as several Calm records, each with its own
    # Sierra bib, all describing one TEI file that names every bib.
    sierra_works = [sierra_physical_work() for _ in range(3)]
    calm_works = [calm_work() for _ in range(3)]
    tei = tei_work(
        merge_candidates=[merge_candidate(w, TEI_BNUMBER_REASON) for w in sierra_works]
    )

    outcome = merger.merge([tei, *sierra_works, *calm_works])

    assert list(visible_works(outcome)) == [tei.state.canonical_id]
    result = redirects(outcome)
    assert same_elements(
        list(result), [w.state.canonical_id for w in sierra_works + calm_works]
    )
    assert set(result.values()) == {tei.state.canonical_id}


def test_a_tei_work_takes_the_items_of_every_sierra_bib_merged_into_it() -> None:
    sierra_works = [sierra_physical_work() for _ in range(3)]
    tei = tei_work(
        merge_candidates=[merge_candidate(w, TEI_BNUMBER_REASON) for w in sierra_works]
    )

    outcome = merger.merge([tei, *sierra_works])

    visible = list(visible_works(outcome).values())
    assert len(visible) == 1
    assert same_elements(
        visible[0].data.items, [i for w in sierra_works for i in w.data.items]
    )


def test_the_thumbnail_is_copied_to_the_inner_works() -> None:
    tei = tei_work(internal_work_stubs=[internal_work_stub()])
    sierra = sierra_physical_work()
    mets_with_thumbnail = mets_work(thumbnail=digital_location())

    outcome = merger.merge([tei, sierra, mets_with_thumbnail])

    for work in visible_works(outcome).values():
        assert work.data.thumbnail == mets_with_thumbnail.data.thumbnail


def test_the_inner_works_of_a_deleted_tei_work_are_deleted() -> None:
    stubs = [internal_work_stub(), internal_work_stub()]
    deleted_tei = deleted(tei_work(internal_work_stubs=stubs))

    outcome = merger.merge([deleted_tei])

    assert len(outcome.works) == 3
    result = deleted_works(outcome)
    assert same_elements(
        [w.state.canonical_id for w in result],
        [deleted_tei.state.canonical_id, *(s.canonical_id for s in stubs)],
    )
    for inner in result:
        if inner.state.canonical_id == deleted_tei.state.canonical_id:
            continue
        assert inner.deleted_reason.type == "TeiDeletedInMerger"
        assert inner.version == deleted_tei.version
        assert (
            inner.state.source_modified_time == deleted_tei.state.source_modified_time
        )


def test_the_inner_works_of_a_deleted_tei_work_are_deleted_alongside_a_visible_target() -> (
    None
):
    stubs = [internal_work_stub(), internal_work_stub()]
    deleted_tei = deleted(tei_work(internal_work_stubs=stubs))
    sierra = sierra_physical_work()

    outcome = merger.merge([sierra, deleted_tei])

    assert len(outcome.works) == 4
    assert list(visible_works(outcome)) == [sierra.state.canonical_id]
    assert same_elements(
        [w.state.canonical_id for w in deleted_works(outcome)],
        [deleted_tei.state.canonical_id, *(s.canonical_id for s in stubs)],
    )


def test_an_inner_work_whose_stub_was_removed_is_deleted() -> None:
    kept = internal_work_stub()
    removed = internal_work_stub()
    tei = tei_work(internal_work_stubs=[kept], removed_internal_work_stubs=[removed])

    outcome = merger.merge([tei])

    assert same_elements(
        list(visible_works(outcome)), [tei.state.canonical_id, kept.canonical_id]
    )
    result = deleted_works(outcome)
    assert [w.state.canonical_id for w in result] == [removed.canonical_id]
    assert result[0].deleted_reason.type == "TeiDeletedInMerger"


def test_removed_inner_works_of_a_tei_work_that_loses_the_merge_are_deleted() -> None:
    # An EBSCO work outranks TEI, so the TEI work's stubs are dropped from the
    # result. The removals still have to happen.
    removed = internal_work_stub()
    tei = tei_work(removed_internal_work_stubs=[removed])
    ebsco = identified_work(schemes.EBSCO_ALT_LOOKUP)

    outcome = merger.merge([ebsco, tei])

    assert [w.state.canonical_id for w in deleted_works(outcome)] == [
        removed.canonical_id
    ]


def test_an_inner_work_just_emitted_is_never_deleted() -> None:
    # The two stub lists should not overlap; if they ever do, the live work wins.
    contested = internal_work_stub()
    tei = tei_work(
        internal_work_stubs=[contested], removed_internal_work_stubs=[contested]
    )

    outcome = merger.merge([tei])

    assert deleted_works(outcome) == []
    assert contested.canonical_id in visible_works(outcome)


def test_a_folio_work_passes_through_unchanged() -> None:
    folio = folio_work()

    outcome = merger.merge([folio])

    assert len(outcome.works) == 1
    assert visible_works(outcome)[folio.state.canonical_id].data == folio.data


def test_an_axiell_work_passes_through_unchanged() -> None:
    axiell = identified_work(schemes.AXIELL_GUID)

    outcome = merger.merge([axiell])

    assert len(outcome.works) == 1
    assert visible_works(outcome)[axiell.state.canonical_id].data == axiell.data


def test_a_born_digital_axiell_work_on_its_own_drops_its_stub_item() -> None:
    axiell = axiell_work(format=ArchivesDigital)

    outcome = merger.merge([axiell])

    assert visible_works(outcome)[axiell.state.canonical_id].data.items == []


# --- FOLIO works merge as Sierra works do ---------------------------------- #
# FOLIO replaces Sierra in its own pipeline, so each Sierra case should merge
# the same way with its Sierra works swapped for FOLIO ones.


def is_sierra(work: IdentifiedWork) -> bool:
    return (
        work.state.source_identifier.identifier_type.id
        == schemes.SIERRA_SYSTEM_NUMBER.id
    )


def with_other_identifiers(
    work: VisibleIdentifiedWork, identifiers: list[SourceIdentifier]
) -> VisibleIdentifiedWork:
    data = work.data.model_copy(update={"other_identifiers": identifiers})
    return work.model_copy(update={"data": data})


def as_sierra(work: IdentifiedWork) -> IdentifiedWork:
    """A Sierra work carries its sierra-identifier, but no other b-number."""
    if isinstance(work, VisibleIdentifiedWork) and is_sierra(work):
        return with_other_identifiers(
            work,
            [
                i
                for i in work.data.other_identifiers
                if i.identifier_type.id != schemes.SIERRA_SYSTEM_NUMBER.id
            ],
        )
    return work


def as_folio(work: IdentifiedWork) -> IdentifiedWork:
    """A FOLIO work is identified by its instance UUID and carries its HRID and b-number."""
    work = as_sierra(work)
    if not (isinstance(work, VisibleIdentifiedWork) and is_sierra(work)):
        return work
    bnumber = work.state.source_identifier
    state = work.state.model_copy(
        update={
            "source_identifier": bnumber.model_copy(
                update={"identifier_type": Id(id=schemes.FOLIO_INSTANCE.id)}
            )
        }
    )
    others = [
        i
        for i in work.data.other_identifiers
        if i.identifier_type.id != schemes.SIERRA_IDENTIFIER.id
    ]
    hrid = bnumber.model_copy(
        update={"identifier_type": Id(id=schemes.FOLIO_INSTANCE_HRID.id)}
    )
    return with_other_identifiers(
        work.model_copy(update={"state": state}), [*others, hrid, bnumber]
    )


def without_record_ids(work: VisibleIdentifiedWork) -> str:
    """The data with the UUID mapped back to the b-number it replaces, as JSON."""
    own_bnumber = work.state.source_identifier.model_copy(
        update={"identifier_type": Id(id=schemes.SIERRA_SYSTEM_NUMBER.id)}
    )
    kept = []
    for i in work.data.other_identifiers:
        if i.identifier_type.id in (
            schemes.FOLIO_INSTANCE_HRID.id,
            schemes.SIERRA_IDENTIFIER.id,
        ):
            continue
        if i == own_bnumber:
            continue
        if i.identifier_type.id == schemes.FOLIO_INSTANCE.id:
            i = i.model_copy(
                update={"identifier_type": Id(id=schemes.SIERRA_SYSTEM_NUMBER.id)}
            )
        if i not in kept:
            kept.append(i)
    return work.data.model_copy(update={"other_identifiers": kept}).model_dump_json()


def normalised_outcome(works: Sequence[IdentifiedWork]) -> tuple[list[str], list[str]]:
    outcome = merger.merge(works)
    merged_works = []
    for w in outcome.works:
        if isinstance(w, VisibleIdentifiedWork):
            sources = sorted(s.canonical_id for s in w.redirect_sources)
            merged_works.append(
                json.dumps([w.state.canonical_id, without_record_ids(w), sources])
            )
        elif isinstance(w, RedirectedIdentifiedWork):
            merged_works.append(
                json.dumps([w.state.canonical_id, w.redirect_target.canonical_id])
            )
        else:
            merged_works.append(json.dumps([w.state.canonical_id, type(w).__name__]))
    images = [
        json.dumps(
            [
                i.image.id.canonical_id,
                [loc.model_dump() for loc in i.image.locations],
                i.parent.id.canonical_id,
            ]
        )
        for i in outcome.images
    ]
    return sorted(merged_works), sorted(images)


tei_for_physical = tei_work(
    merge_candidates=[merge_candidate(sierra_physical, PHYSICAL_DIGITAL_REASON)]
)
axiell = axiell_work()
bib_with_axiell = sierra_physical_work(
    merge_candidates=[merge_candidate(axiell, AXIELL_SIERRA_REASON)]
)
ebib_with_ebsco, ebsco = sierra_ebsco_pair()
physical_video = sierra_work(format=Film, items=[physical_item()])
video_ebib = sierra_work(
    format=Videos,
    merge_candidates=[merge_candidate(physical_video, PHYSICAL_DIGITAL_REASON)],
)
video_mets = mets_for(video_ebib)
shared_miro = miro_work()


def bibs_sharing(
    miro: VisibleIdentifiedWork, format: Format
) -> list[VisibleIdentifiedWork]:
    return [
        sierra_physical_work(
            format=format, merge_candidates=[merge_candidate(miro, MIRO_SIERRA_REASON)]
        )
        for _ in range(2)
    ]


pictures_sharing_miro = bibs_sharing(shared_miro, Pictures)
mets_for_pictures = [mets_for(bib) for bib in pictures_sharing_miro]
axiell_for_bibs = [axiell_work() for _ in range(2)]
bibs_with_axiell_sharing_miro = [
    sierra_physical_work(
        merge_candidates=[
            merge_candidate(a, AXIELL_SIERRA_REASON),
            merge_candidate(shared_miro, MIRO_SIERRA_REASON),
        ]
    )
    for a in axiell_for_bibs
]

FOLIO_EXAMPLES: list[tuple[list[IdentifiedWork], str]] = [
    ([sierra_physical, mets], "physical work and METS"),
    ([sierra_picture, mets], "picture and METS"),
    ([zero_item_sierra, miro], "zero-item picture and Miro"),
    ([sierra_digital, miro], "digital image and Miro"),
    ([multiple_items_sierra, miro], "multi-item work and Miro"),
    ([sierra_digitised, sierra_physical, mets], "physical/digitised pair and METS"),
    (
        [sierra_digitised, sierra_physical, mets, miro],
        "physical/digitised pair, METS and Miro",
    ),
    ([calm, sierra_physical, mets], "Calm, physical and METS"),
    ([tei_for_physical, sierra_physical], "TEI and physical work"),
    ([axiell, bib_with_axiell, mets], "Axiell, physical and METS"),
    ([ebib_with_ebsco, ebsco], "EBSCO and e-bib"),
    (
        [physical_video, video_ebib, video_mets],
        "audiovisual e-bib, physical video and METS",
    ),
    (
        [shared_miro, *pictures_sharing_miro, *mets_for_pictures],
        "bibs sharing a Miro, each with METS",
    ),
    (
        [shared_miro, *axiell_for_bibs, *bibs_with_axiell_sharing_miro],
        "bibs sharing a Miro, each with an Axiell work",
    ),
]


@pytest.mark.parametrize(
    "works", [e[0] for e in FOLIO_EXAMPLES], ids=[e[1] for e in FOLIO_EXAMPLES]
)
def test_folio_works_choose_the_same_target_as_sierra_works(
    works: list[IdentifiedWork],
) -> None:
    folio_target = merger.find_target([as_folio(w) for w in works])
    sierra_target = merger.find_target([as_sierra(w) for w in works])

    assert folio_target is not None and sierra_target is not None
    assert folio_target.state.canonical_id == sierra_target.state.canonical_id


@pytest.mark.parametrize(
    "works", [e[0] for e in FOLIO_EXAMPLES], ids=[e[1] for e in FOLIO_EXAMPLES]
)
def test_folio_works_produce_the_same_merged_works_and_images_as_sierra_works(
    works: list[IdentifiedWork],
) -> None:
    folio_works = [as_folio(w) for w in works]

    assert any(
        w.state.source_identifier.identifier_type.id == schemes.FOLIO_INSTANCE.id
        for w in folio_works
    )
    assert normalised_outcome(folio_works) == normalised_outcome(
        [as_sierra(w) for w in works]
    )


# --- bibs that link the same Miro work ------------------------------------- #
# See platform issue 6750.


def test_each_bib_sharing_a_miro_work_gets_its_own_mets_work() -> None:
    miro_shared = miro_work()
    bibs = bibs_sharing(miro_shared, Pictures)
    mets_for_bibs = [mets_for(bib) for bib in bibs]
    works = [miro_shared, *bibs, *mets_for_bibs]
    target = merger.find_target(works)
    assert target is not None

    outcome = merger.merge(works)

    assert redirects(outcome) == {
        mets_for_bibs[0].state.canonical_id: bibs[0].state.canonical_id,
        mets_for_bibs[1].state.canonical_id: bibs[1].state.canonical_id,
        miro_shared.state.canonical_id: target.state.canonical_id,
    }
    visible = visible_works(outcome)
    for bib, bib_mets in zip(bibs, mets_for_bibs, strict=True):
        locations = [
            loc
            for item in visible[bib.state.canonical_id].data.items
            for loc in item.locations
        ]
        for expected in (
            loc for w in (bib, bib_mets) for i in w.data.items for loc in i.locations
        ):
            assert expected in locations


def test_a_bib_sharing_a_miro_work_without_its_own_mets_work_is_unchanged() -> None:
    miro_shared = miro_work()
    bibs = bibs_sharing(miro_shared, Books)
    mets_for_second = mets_for(bibs[1])

    outcome = merger.merge([miro_shared, *bibs, mets_for_second])

    assert redirects(outcome)[mets_for_second.state.canonical_id] == (
        bibs[1].state.canonical_id
    )
    assert visible_works(outcome)[bibs[0].state.canonical_id].data.items == (
        bibs[0].data.items
    )


def test_a_bib_gets_the_miro_work_that_only_it_links() -> None:
    miro_shared = miro_work()
    own_miro = miro_work()
    target_bib = sierra_physical_work(
        format=Pictures,
        merge_candidates=[merge_candidate(miro_shared, MIRO_SIERRA_REASON)],
    )
    other_bib = sierra_physical_work(
        format=Pictures,
        merge_candidates=[
            merge_candidate(miro_shared, MIRO_SIERRA_REASON),
            merge_candidate(own_miro, MIRO_SIERRA_REASON),
        ],
    )
    works = [target_bib, other_bib, miro_shared, own_miro]
    assert merger.find_target(works) == target_bib

    outcome = merger.merge(works)

    assert redirects(outcome) == {
        miro_shared.state.canonical_id: target_bib.state.canonical_id,
        own_miro.state.canonical_id: other_bib.state.canonical_id,
    }


def test_the_target_never_gets_a_miro_work_it_does_not_link() -> None:
    miro_ab = miro_work()
    miro_bc = miro_work()

    def picture_bib(*miros: VisibleIdentifiedWork) -> VisibleIdentifiedWork:
        return sierra_physical_work(
            format=Pictures,
            merge_candidates=[merge_candidate(m, MIRO_SIERRA_REASON) for m in miros],
        )

    bib_a = picture_bib(miro_ab)
    bib_b = picture_bib(miro_ab, miro_bc)
    bib_c = picture_bib(miro_bc)
    works = [bib_a, bib_b, bib_c, miro_ab, miro_bc]
    assert merger.find_target(works) == bib_a

    outcome = merger.merge(works)

    assert redirects(outcome) == {
        miro_ab.state.canonical_id: bib_a.state.canonical_id,
        miro_bc.state.canonical_id: bib_b.state.canonical_id,
    }


def test_each_axiell_work_gets_only_its_own_bib() -> None:
    miro_shared = miro_work()
    axiell_works = [axiell_work() for _ in range(2)]
    bibs = [
        sierra_physical_work(
            merge_candidates=[
                merge_candidate(a, AXIELL_SIERRA_REASON),
                merge_candidate(miro_shared, MIRO_SIERRA_REASON),
            ]
        )
        for a in axiell_works
    ]
    works = [miro_shared, *axiell_works, *bibs]
    target = merger.find_target(works)
    assert target is not None

    outcome = merger.merge(works)

    assert redirects(outcome) == {
        bibs[0].state.canonical_id: axiell_works[0].state.canonical_id,
        bibs[1].state.canonical_id: axiell_works[1].state.canonical_id,
        miro_shared.state.canonical_id: target.state.canonical_id,
    }
    visible = visible_works(outcome)
    for a, bib in zip(axiell_works, bibs, strict=True):
        item_ids = [i.id for i in visible[a.state.canonical_id].data.items]
        assert bib.data.items[0].id in item_ids
