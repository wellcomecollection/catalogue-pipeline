"""The predicates pick out works by source and by the shape of their items.

Scala also checks that a CALM work is not an Axiell work and the reverse. The Python
merger has one CMS predicate for both, so those two cases have no counterpart here.
"""

import pytest

from merger.rules import predicates
from models import identifier_schemes as schemes
from models.pipeline.format import (
    ArchivesDigital,
    Books,
    DigitalImages,
    Ephemera,
    Music,
    Pictures,
    ThreeDObjects,
    Videos,
)
from models.pipeline.identified.work import IdentifiedWork
from models.pipeline.location import DigitalLocation, PhysicalLocation
from tests.merger.works import (
    axiell_work,
    calm_work,
    digcode_identifier,
    digital_item,
    ebsco_work,
    folio_work,
    invisible,
    mets_work,
    miro_work,
    physical_item,
    physical_location,
    sierra_digital_work,
    sierra_pair,
    sierra_physical_work,
    sierra_work,
    tei_work,
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


def test_calm_and_axiell_works_are_single_physical_item_cms_works() -> None:
    assert predicates.single_physical_item_cms_work(calm_work())
    assert predicates.single_physical_item_cms_work(axiell_work())


def test_predicates_combine_with_and_or_and_not() -> None:
    is_sierra = predicates.source(schemes.SIERRA_SYSTEM_NUMBER)
    is_picture = predicates.has_format(Pictures)
    is_music = predicates.has_format(Music)
    work = sierra_work(format=Pictures)

    assert (is_sierra & is_picture)(work)
    assert not (is_sierra & is_music)(work)
    assert (is_music | is_picture)(work)
    assert not (is_music | predicates.multi_item)(work)
    assert (~is_music)(work)
    assert not (~is_picture)(work)


DIGITISED, PHYSICAL = sierra_pair()


@pytest.mark.parametrize(
    ("predicate", "work", "expected"),
    [
        pytest.param(predicates.ebsco_work, ebsco_work(), True, id="an EBSCO work"),
        pytest.param(
            predicates.ebsco_work,
            sierra_work(),
            False,
            id="a Sierra work is not an EBSCO work",
        ),
        pytest.param(
            predicates.tei_work, tei_work(), True, id="a TEI work with no items"
        ),
        pytest.param(
            predicates.tei_work,
            tei_work(items=[physical_item()]),
            False,
            id="a TEI work with an item",
        ),
        pytest.param(
            predicates.tei_work,
            invisible(tei_work()),
            False,
            id="an invisible TEI work",
        ),
        pytest.param(
            predicates.zero_item_lms_work,
            sierra_work(),
            True,
            id="an LMS work with no items",
        ),
        pytest.param(
            predicates.zero_item_lms_work,
            sierra_physical_work(),
            False,
            id="an LMS work with an item is not a zero-item work",
        ),
        pytest.param(
            predicates.multi_item_lms_work,
            sierra_work(items=[physical_item(), physical_item()]),
            True,
            id="an LMS work with two items",
        ),
        pytest.param(
            predicates.multi_item_lms_work,
            sierra_physical_work(),
            False,
            id="an LMS work with one item is not a multi-item work",
        ),
        pytest.param(
            predicates.multi_item_lms_work,
            calm_work(items=[physical_item(), physical_item()]),
            False,
            id="a CMS work with two items is not an LMS work",
        ),
        pytest.param(
            predicates.lms_work_picture_or_ephemera,
            sierra_work(format=Pictures),
            True,
            id="an LMS picture",
        ),
        pytest.param(
            predicates.lms_work_picture_or_ephemera,
            sierra_work(format=Ephemera),
            True,
            id="LMS ephemera",
        ),
        pytest.param(
            predicates.lms_work_picture_or_ephemera,
            sierra_work(format=DigitalImages),
            False,
            id="an LMS digital image is neither picture nor ephemera",
        ),
        pytest.param(
            predicates.lms_work_digitised_miro,
            sierra_work(other_identifiers=[digcode_identifier("digmiro")]),
            True,
            id="the digmiro digcode",
        ),
        pytest.param(
            predicates.lms_work_digitised_miro,
            sierra_work(other_identifiers=[digcode_identifier("digcom")]),
            False,
            id="another digcode",
        ),
        pytest.param(
            predicates.lms_work_digitised_av,
            sierra_digital_work(format=Videos),
            True,
            id="an audiovisual LMS work with only unidentified items",
        ),
        pytest.param(
            predicates.lms_work_digitised_av,
            sierra_physical_work(format=Videos),
            False,
            id="an audiovisual LMS work with an identified item",
        ),
        pytest.param(
            predicates.lms_work_digitised_av,
            sierra_digital_work(format=Books),
            False,
            id="a non-audiovisual LMS work",
        ),
        pytest.param(
            predicates.single_physical_item_cms_work,
            calm_work(items=[physical_item(), physical_item()]),
            False,
            id="a CMS work with two items is not a single physical item CMS work",
        ),
        pytest.param(
            predicates.single_physical_item_cms_work,
            calm_work(items=[digital_item()]),
            False,
            id="a CMS work with a digital item is not a single physical item CMS work",
        ),
        pytest.param(
            predicates.single_physical_item_cms_work,
            calm_work(
                items=[
                    physical_item(locations=[physical_location(), physical_location()])
                ]
            ),
            False,
            id="a CMS work with an item in two locations is not a single physical item CMS work",
        ),
        pytest.param(
            predicates.single_physical_item_cms_work,
            sierra_physical_work(),
            False,
            id="an LMS work with one physical item is not a CMS work",
        ),
        pytest.param(
            predicates.born_digital_archive_stub_work,
            calm_work(format=ArchivesDigital),
            True,
            id="a born-digital CMS work",
        ),
        pytest.param(
            predicates.born_digital_archive_stub_work,
            calm_work(),
            False,
            id="a CMS work of another format",
        ),
        pytest.param(
            predicates.physical_digital,
            PHYSICAL,
            True,
            id="the physical side of a digitised pair",
        ),
        pytest.param(
            predicates.physical_digital,
            DIGITISED,
            False,
            id="the digitised side of a digitised pair",
        ),
    ],
)
def test_predicate(
    predicate: predicates.Predicate, work: IdentifiedWork, expected: bool
) -> None:
    assert predicate(work) is expected
