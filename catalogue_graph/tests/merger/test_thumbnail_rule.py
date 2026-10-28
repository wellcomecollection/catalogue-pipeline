"""Choosing the thumbnail of a merged work."""

import pytest

from merger.rules.thumbnail import ThumbnailRule
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import ManualRequest, ViewOnline
from models.pipeline.access_status import Open, Restricted
from models.pipeline.identified.work import VisibleIdentifiedWork
from models.pipeline.identifier import Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation
from tests.merger.works import (
    CC_BY,
    ThumbnailImage,
    axiell_work,
    calm_work,
    digital_item,
    digital_location,
    mets_work,
    miro_work,
    physical_item,
    physical_location,
    same_elements,
    sierra_digital_work,
    sierra_physical_work,
    sierra_work,
    tei_work,
)

physical_sierra = sierra_physical_work()
digital_sierra = sierra_digital_work()
calm = calm_work()
axiell = axiell_work()
tei = tei_work()
mets = mets_work(
    thumbnail=DigitalLocation(
        url="mets.com/thumbnail.jpg",
        location_type=ThumbnailImage,
        license=CC_BY,
        access_conditions=[],
    )
)
miro_works = [miro_work() for _ in range(4)]

restricted = AccessCondition(method=ManualRequest, status=Restricted)
open_online = AccessCondition(method=ViewOnline, status=Open)
restricted_online = AccessCondition(method=ViewOnline, status=Restricted)
open_physical = AccessCondition(method=ManualRequest, status=Open)

restricted_physical_work = sierra_work(
    items=[physical_item(identified=False, locations=[physical_location([restricted])])]
)
work_with_physical_restriction = sierra_work(
    items=[
        Item(
            id=Unidentifiable(),
            locations=[
                physical_location([restricted]),
                digital_location(access_conditions=[open_online]),
            ],
        )
    ]
)
work_with_digital_restriction = sierra_work(
    items=[
        Item(
            id=Unidentifiable(),
            locations=[
                physical_location([open_physical]),
                digital_location(access_conditions=[restricted_online]),
            ],
        )
    ]
)
restricted_digital_work = sierra_work(
    items=[digital_item([digital_location(access_conditions=[restricted_online])])]
)


@pytest.mark.parametrize("target", [digital_sierra, calm, axiell, tei])
def test_the_mets_thumbnail_is_chosen(target: VisibleIdentifiedWork) -> None:
    result = ThumbnailRule.merge(target, [*miro_works, mets])

    assert result.data is not None
    assert result.data == mets.data.thumbnail


@pytest.mark.parametrize("target", [tei, digital_sierra, physical_sierra])
def test_a_miro_thumbnail_is_chosen_without_mets(target: VisibleIdentifiedWork) -> None:
    result = ThumbnailRule.merge(target, miro_works)

    assert result.data is not None
    assert result.data in [work.data.thumbnail for work in miro_works]


def test_the_miro_thumbnail_comes_from_the_work_with_the_smallest_id() -> None:
    result = ThumbnailRule.merge(physical_sierra, miro_works)

    smallest = min(miro_works, key=lambda work: work.state.source_identifier.value)
    assert result.data is not None
    assert result.data == smallest.data.thumbnail


@pytest.mark.parametrize(
    "target", [restricted_digital_work, work_with_digital_restriction]
)
def test_a_restricted_digital_location_suppresses_the_thumbnail(
    target: VisibleIdentifiedWork,
) -> None:
    assert ThumbnailRule.merge(target, [*miro_works, mets]).data is None


@pytest.mark.parametrize(
    "target", [restricted_physical_work, work_with_physical_restriction]
)
def test_a_restriction_only_on_a_physical_location_does_not_suppress_it(
    target: VisibleIdentifiedWork,
) -> None:
    assert ThumbnailRule.merge(target, [*miro_works, mets]).data is not None


def test_the_works_whose_thumbnails_were_considered_are_merged_sources() -> None:
    result = ThumbnailRule.merge(digital_sierra, [*miro_works, mets])

    assert same_elements(result.sources, [*miro_works, mets])
