"""The predicates pick out works by source and by the shape of their items."""

from merger.rules import predicates
from models.pipeline.format import DigitalImages, Music, Pictures, ThreeDObjects
from models.pipeline.identified.work import IdentifiedWork
from models.pipeline.location import DigitalLocation, PhysicalLocation
from tests.merger.works import (
    axiell_work,
    calm_work,
    digcode_identifier,
    digital_item,
    folio_work,
    invisible,
    mets_work,
    miro_work,
    physical_item,
    sierra_digital_work,
    sierra_physical_work,
    sierra_work,
)

WORKS: list[IdentifiedWork] = [
    sierra_work(),
    miro_work(),
    invisible(mets_work()),
    invisible(mets_work(items=[digital_item() for _ in range(4)])),
    miro_work(items=[digital_item() for _ in range(3)]),
    sierra_work(items=[digital_item() for _ in range(4)]),
    sierra_physical_work(),
    sierra_digital_work(),
    sierra_work(format=ThreeDObjects),
    sierra_work(format=DigitalImages),
    sierra_work(format=Pictures),
    sierra_work(format=Music),
    sierra_work(other_identifiers=[digcode_identifier("digaids")]),
    folio_work(),
]


def scheme_of(work: IdentifiedWork) -> str:
    return work.state.source_identifier.identifier_type.id


def test_sierra_and_folio_works_are_lms_works() -> None:
    selected = [work for work in WORKS if predicates.lms_work(work)]

    assert {scheme_of(work) for work in selected} == {
        "sierra-system-number",
        "folio-instance",
    }


def test_single_digital_item_mets_works() -> None:
    selected = [
        work for work in WORKS if predicates.single_digital_item_mets_work(work)
    ]

    assert selected
    for work in selected:
        assert scheme_of(work) == "mets"
        assert len(work.data.items) == 1
        assert isinstance(work.data.items[0].locations[0], DigitalLocation)


def test_single_digital_item_miro_works() -> None:
    selected = [
        work for work in WORKS if predicates.single_digital_item_miro_work(work)
    ]

    assert selected
    for work in selected:
        assert scheme_of(work) == "miro-image-number"
        assert len(work.data.items) == 1
        assert isinstance(work.data.items[0].locations[0], DigitalLocation)


def test_single_item_lms_works() -> None:
    selected = [work for work in WORKS if predicates.single_item_lms_work(work)]

    assert selected
    for work in selected:
        assert scheme_of(work) == "sierra-system-number"
        assert len(work.data.items) == 1


def test_physical_lms_works() -> None:
    selected = [work for work in WORKS if predicates.physical_lms_work(work)]

    assert selected
    for work in selected:
        assert scheme_of(work) == "sierra-system-number"
        locations = [loc for item in work.data.items for loc in item.locations]
        assert any(isinstance(loc, PhysicalLocation) for loc in locations)


def test_picture_digital_image_or_3d_object_lms_works() -> None:
    selected = [
        work
        for work in WORKS
        if predicates.lms_work_picture_digital_image_or_3d_object(work)
    ]

    assert selected
    for work in selected:
        assert work.data.format in (Pictures, DigitalImages, ThreeDObjects)


def test_lms_works_with_the_digaids_digcode() -> None:
    selected = [work for work in WORKS if predicates.lms_work_digitised_miro(work)]

    assert selected
    for work in selected:
        identifiers = work.data.other_identifiers
        assert "wellcome-digcode" in [i.identifier_type.id for i in identifiers]
        assert "digaids" in [i.value for i in identifiers]


def test_single_physical_item_axiell_work() -> None:
    assert predicates.single_physical_item_axiell_work(axiell_work())


def test_a_calm_work_is_not_a_single_physical_item_axiell_work() -> None:
    work = calm_work(items=[physical_item(identified=False)])

    assert not predicates.single_physical_item_axiell_work(work)


def test_an_axiell_work_is_not_a_single_physical_item_calm_work() -> None:
    work = axiell_work(items=[physical_item(identified=False)])

    assert not predicates.single_physical_item_calm_work(work)
