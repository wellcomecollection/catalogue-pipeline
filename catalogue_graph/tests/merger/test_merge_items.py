"""Merging the items of a target and its sources."""

from merger.rules.items import merge_items
from merger.rules.predicates import PHYSICAL_DIGITAL_REASON
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import OnlineRequest, ViewOnline
from models.pipeline.access_status import LicensedResource, Open
from models.pipeline.format import ArchivesDigital, Pictures
from models.pipeline.item import Item
from tests.merger.works import (
    Maps,
    axiell_work,
    calm_work,
    digcode_identifier,
    digital_item,
    invisible,
    merge_candidate,
    mets_work,
    miro_work,
    online_location,
    physical_item,
    physical_location,
    same_elements,
    sierra_pair,
    sierra_physical_work,
    sierra_work,
    tei_work,
)

tei = tei_work()
physical_picture_sierra = sierra_physical_work(format=Pictures)
physical_maps_sierra = sierra_physical_work(format=Maps)
zero_item_physical_sierra = sierra_work(format=Pictures)
multi_item_physical_sierra = sierra_work(items=[physical_item(), physical_item()])
mets = invisible(mets_work())
miro = miro_work()
calm = calm_work()
axiell = axiell_work()


def mergeable_digital_item() -> Item:
    """An 856 item: an online resource that is a licensed resource."""
    condition = AccessCondition(method=ViewOnline, status=LicensedResource)
    return digital_item([online_location([condition])])


def test_a_digitised_sierra_work_is_a_merged_source_but_leaves_items_unchanged() -> (
    None
):
    digitised, physical = sierra_pair()

    result = merge_items(physical, [digitised])

    assert result.value == physical.data.items
    assert result.claimed == [digitised]


def test_the_miro_item_is_merged_into_a_zero_item_sierra_work() -> None:
    result = merge_items(zero_item_physical_sierra, [miro])

    assert result.value == [miro.data.items[0]]
    assert result.claimed == [miro]


def test_sierra_items_are_merged_into_a_tei_work() -> None:
    result = merge_items(tei, [multi_item_physical_sierra])

    assert result.value == multi_item_physical_sierra.data.items
    assert result.claimed == [multi_item_physical_sierra]


def test_the_sierra_item_replaces_the_calm_item_on_a_tei_work() -> None:
    result = merge_items(tei, [physical_picture_sierra, calm])

    assert result.value == physical_picture_sierra.data.items
    assert result.claimed == [physical_picture_sierra]


def test_the_mets_item_is_merged_into_a_zero_item_sierra_work() -> None:
    result = merge_items(zero_item_physical_sierra, [mets])

    assert result.value == mets.data.items
    assert result.claimed == [mets]


def test_an_856_item_from_the_digitised_work_is_merged_into_the_physical_work() -> None:
    digitised = sierra_work(items=[mergeable_digital_item()])
    physical = sierra_work(
        items=[physical_item()],
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )

    result = merge_items(physical, [digitised])

    assert same_elements(result.value, physical.data.items + digitised.data.items)
    assert result.claimed == [digitised]


def test_the_first_digital_work_is_chosen_among_non_digital_sierra_sources() -> None:
    digitised = sierra_work(items=[mergeable_digital_item()])
    physical = sierra_work(
        items=[physical_item()],
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )
    other_physical = sierra_work(items=[physical_item()])

    result = merge_items(physical, [other_physical, digitised])

    assert same_elements(result.value, physical.data.items + digitised.data.items)
    assert result.claimed == [digitised]


def test_the_physical_work_keeps_its_items_with_no_sources() -> None:
    digitised = sierra_work(items=[mergeable_digital_item()])
    physical = sierra_work(
        items=[physical_item()],
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )

    result = merge_items(physical, [])

    assert result.value == physical.data.items
    assert result.claimed == []


def test_the_physical_work_keeps_its_items_when_the_digitised_work_is_absent() -> None:
    digitised = sierra_work(items=[mergeable_digital_item()])
    physical = sierra_work(
        items=[physical_item()],
        merge_candidates=[merge_candidate(digitised, PHYSICAL_DIGITAL_REASON)],
    )

    result = merge_items(physical, [sierra_work()])

    assert result.value == physical.data.items
    assert result.claimed == []


def test_only_the_first_856_item_in_the_merge_candidates_is_merged() -> None:
    digitised_0 = sierra_work(items=[mergeable_digital_item()])
    digitised_1 = sierra_work(items=[mergeable_digital_item()])
    item = physical_item()
    physical = sierra_work(
        items=[item],
        merge_candidates=[
            merge_candidate(digitised_0, PHYSICAL_DIGITAL_REASON),
            merge_candidate(digitised_1, PHYSICAL_DIGITAL_REASON),
        ],
    )

    # The sources are in the reverse order of the merge candidates, which win.
    result = merge_items(physical, [digitised_1, digitised_0])

    assert same_elements(result.value, [item] + digitised_0.data.items)
    assert result.claimed == [digitised_0]


def test_sierra_items_are_not_merged_into_a_target_without_a_digitised_pair() -> None:
    result = merge_items(multi_item_physical_sierra, [miro, physical_maps_sierra])

    assert result.value == multi_item_physical_sierra.data.items
    assert result.claimed == []


def test_miro_locations_are_merged_into_the_single_sierra_item() -> None:
    result = merge_items(physical_picture_sierra, [miro])

    assert len(result.value) == 1
    assert same_elements(
        result.value[0].locations,
        physical_picture_sierra.data.items[0].locations + miro.data.items[0].locations,
    )
    assert result.claimed == [miro]


def test_mets_locations_are_merged_into_the_single_sierra_item() -> None:
    result = merge_items(physical_picture_sierra, [mets])

    assert len(result.value) == 1
    assert (
        result.value[0].locations
        == physical_picture_sierra.data.items[0].locations
        + mets.data.items[0].locations
    )
    assert result.claimed == [mets]


def test_no_miro_source_is_merged_when_there_are_several() -> None:
    result = merge_items(physical_picture_sierra, [miro, miro_work()])

    assert result.value == physical_picture_sierra.data.items
    assert result.claimed == []


def test_a_miro_source_is_not_merged_into_a_sierra_work_of_another_format() -> None:
    result = merge_items(physical_maps_sierra, [miro])

    assert result.value == physical_maps_sierra.data.items
    assert result.claimed == []


def test_mets_merging_overrides_miro_merging_into_the_single_sierra_item() -> None:
    result = merge_items(physical_picture_sierra, [miro, mets])

    assert len(result.value) == 1
    assert (
        result.value[0].locations
        == physical_picture_sierra.data.items[0].locations
        + mets.data.items[0].locations
    )
    assert same_elements(result.claimed, [mets, miro])


def test_miro_works_are_not_merged_into_multi_item_sierra_works() -> None:
    result = merge_items(multi_item_physical_sierra, [miro])

    assert result.value == multi_item_physical_sierra.data.items
    assert result.claimed == []


def test_mets_items_are_added_to_multi_item_sierra_works() -> None:
    result = merge_items(multi_item_physical_sierra, [mets])

    assert same_elements(
        result.value, multi_item_physical_sierra.data.items + mets.data.items
    )
    assert result.claimed == [mets]


def test_miro_works_are_claimed_by_a_digaids_work_without_contributing() -> None:
    digaids = sierra_work(
        items=[physical_item(), physical_item()],
        other_identifiers=[digcode_identifier("digaids")],
    )

    result = merge_items(digaids, [miro, mets])

    assert result.value == digaids.data.items + mets.data.items
    assert same_elements(result.claimed, [miro, mets])


def test_the_sierra_item_id_replaces_the_calm_item() -> None:
    result = merge_items(calm, [physical_picture_sierra])

    assert len(result.value) == 1
    assert result.value[0].id == physical_picture_sierra.data.items[0].id
    assert result.claimed == [physical_picture_sierra]


def test_sierra_locations_are_copied_onto_a_calm_work() -> None:
    location = physical_location([AccessCondition(method=OnlineRequest, status=Open)])
    sierra = sierra_work(items=[physical_item(locations=[location])])

    result = merge_items(calm, [sierra])

    assert len(result.value) == 1
    assert result.value[0].locations == [location]
    assert result.claimed == [sierra]


def test_sierra_and_mets_items_replace_the_calm_item() -> None:
    result = merge_items(calm, [physical_picture_sierra, mets])

    assert result.value == physical_picture_sierra.data.items + mets.data.items


def test_the_sierra_item_id_replaces_the_axiell_item() -> None:
    result = merge_items(axiell, [physical_picture_sierra])

    assert len(result.value) == 1
    assert result.value[0].id == physical_picture_sierra.data.items[0].id
    assert result.claimed == [physical_picture_sierra]


def test_sierra_locations_are_copied_onto_an_axiell_work() -> None:
    location = physical_location([AccessCondition(method=OnlineRequest, status=Open)])
    sierra = sierra_work(items=[physical_item(locations=[location])])

    result = merge_items(axiell, [sierra])

    assert len(result.value) == 1
    assert result.value[0].locations == [location]
    assert result.claimed == [sierra]


def test_sierra_and_mets_items_replace_the_axiell_item() -> None:
    result = merge_items(axiell, [physical_picture_sierra, mets])

    assert result.value == physical_picture_sierra.data.items + mets.data.items


def test_a_born_digital_axiell_work_drops_its_stub_item_when_unmerged() -> None:
    born_digital = axiell_work(format=ArchivesDigital)

    assert merge_items(born_digital, []).value == []


def test_a_born_digital_axiell_work_takes_the_mets_item() -> None:
    born_digital = axiell_work(format=ArchivesDigital)

    assert merge_items(born_digital, [mets]).value == mets.data.items


def test_a_born_digital_axiell_work_takes_the_sierra_items() -> None:
    born_digital = axiell_work(format=ArchivesDigital)

    result = merge_items(born_digital, [physical_picture_sierra])

    assert result.value == physical_picture_sierra.data.items


def test_a_born_digital_calm_work_drops_its_stub_item_when_unmerged() -> None:
    born_digital = calm_work(format=ArchivesDigital)

    assert merge_items(born_digital, []).value == []


def test_an_axiell_work_that_is_not_born_digital_keeps_its_stub_item() -> None:
    assert merge_items(axiell, []).value == axiell.data.items
