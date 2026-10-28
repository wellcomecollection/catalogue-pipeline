"""The images emitted from a merge as image records."""

import pytest

from merger.rules.images import image_data_rule, images_rule
from models.pipeline.format import (
    FORMAT_LABEL_MAPPING,
    Books,
    Ephemera,
    Format,
    Pictures,
)
from models.pipeline.identified.work import VisibleIdentifiedWork
from tests.merger.works import (
    IN_COPYRIGHT,
    digcode_identifier,
    digital_item,
    invisible_mets_work,
    miro_work,
    physical_item,
    same_elements,
    sierra_digital_work,
    sierra_work,
)


def test_one_image_per_miro_work_merged_with_a_sierra_work() -> None:
    miro_works = [miro_work() for _ in range(3)]

    result = image_data_rule.merge(sierra_digital_work(), miro_works)

    assert len(result.data) == 3
    assert same_elements(
        [i.locations for i in result.data],
        [w.data.image_data[0].locations for w in miro_works],
    )


@pytest.mark.parametrize("format", [Pictures, Ephemera])
def test_every_mets_image_for_a_sierra_picture_or_ephemera_work(format: Format) -> None:
    mets = invisible_mets_work(num_images=5)

    result = images_rule.merge(sierra_work(format=format), [mets])

    assert len(result.data) == 5
    assert same_elements(
        [i.locations for i in result.data], [i.locations for i in mets.data.image_data]
    )


@pytest.mark.parametrize("format", [Pictures, Ephemera])
def test_mets_and_miro_images_for_a_sierra_picture_or_ephemera_work(
    format: Format,
) -> None:
    miro_works = [miro_work() for _ in range(4)]
    mets = invisible_mets_work(num_images=3)

    result = images_rule.merge(sierra_work(format=format), [*miro_works, mets])

    assert len(result.data) == 7
    assert same_elements(
        [i.locations for i in result.data],
        [i.locations for i in mets.data.image_data]
        + [w.data.image_data[0].locations for w in miro_works],
    )


def test_the_mets_licence_overrides_the_miro_licence() -> None:
    miro_works = [miro_work() for _ in range(4)]
    expected_miro_locations = [
        [loc.model_copy(update={"license": IN_COPYRIGHT}) for loc in image.locations]
        for work in miro_works
        for image in work.data.image_data
    ]
    mets = invisible_mets_work(num_images=3, license=IN_COPYRIGHT)

    result = images_rule.merge(sierra_work(format=Ephemera), [*miro_works, mets])

    assert len(result.data) == 7
    assert same_elements(
        [i.locations for i in result.data],
        [i.locations for i in mets.data.image_data] + expected_miro_locations,
    )


def test_mets_images_are_ignored_for_other_formats_but_miro_images_are_used() -> None:
    mets = invisible_mets_work(num_images=3)
    miro_works = [miro_work() for _ in range(3)]

    result = images_rule.merge(sierra_digital_work(), [*miro_works, mets])

    assert len(result.data) == 3
    assert same_elements(
        [i.locations for i in result.data],
        [w.data.image_data[0].locations for w in miro_works],
    )


@pytest.mark.parametrize("digcode", ["digaids", "digmiro"])
def test_miro_images_are_dropped_when_a_re_digitised_work_has_a_mets_image(
    digcode: str,
) -> None:
    mets = invisible_mets_work(num_images=1)
    sierra = sierra_work(
        format=Pictures, other_identifiers=[digcode_identifier(digcode)]
    )

    result = images_rule.merge(sierra, [miro_work(), mets])

    assert same_elements(result.data, mets.data.image_data)


def test_no_images_are_kept_on_a_re_digitised_work_with_several_mets_images() -> None:
    mets = invisible_mets_work(num_images=4)
    sierra = sierra_work(
        format=Books, other_identifiers=[digcode_identifier("digmiro")]
    )

    assert image_data_rule.merge(sierra, [miro_work(), mets]).data == []


def test_miro_images_are_dropped_when_several_miro_and_mets_images_are_present() -> (
    None
):
    mets = invisible_mets_work(num_images=4)
    sierra = sierra_work(
        format=Pictures, other_identifiers=[digcode_identifier("digmiro")]
    )

    result = images_rule.merge(sierra, [miro_work(), miro_work(), miro_work(), mets])

    assert same_elements(result.data, mets.data.image_data)


def test_miro_images_are_kept_without_a_re_digitised_digcode() -> None:
    mets = invisible_mets_work(num_images=1)
    miro = miro_work()
    sierra = sierra_work(
        format=Pictures, other_identifiers=[digcode_identifier("digicon")]
    )

    result = images_rule.merge(sierra, [miro, mets])

    assert len(result.data) == 2
    assert same_elements(result.data, mets.data.image_data + miro.data.image_data)


def test_a_re_digitised_work_is_recognised_among_other_digcodes() -> None:
    mets = invisible_mets_work(num_images=1)
    sierra = sierra_work(
        format=Pictures,
        other_identifiers=[
            digcode_identifier("CBM"),
            digcode_identifier("digmiro"),
            digcode_identifier("WOAM"),
        ],
    )

    result = images_rule.merge(sierra, [miro_work(), mets])

    assert same_elements(result.data, mets.data.image_data)


def test_a_re_digitised_work_is_recognised_when_an_unmarked_sierra_work_is_the_target() -> (
    None
):
    mets = invisible_mets_work(num_images=1)
    sierra_digmiro = sierra_work(
        format=Pictures, other_identifiers=[digcode_identifier("digmiro")]
    )

    result = images_rule.merge(sierra_work(), [miro_work(), sierra_digmiro, mets])

    assert same_elements(result.data, mets.data.image_data)


ALL_FORMATS = [Format(id=i, label=label) for i, label in FORMAT_LABEL_MAPPING.items()]


def re_digitised_sierra(format: Format) -> VisibleIdentifiedWork:
    return sierra_work(
        format=format,
        other_identifiers=[digcode_identifier("digmiro")],
        items=[physical_item(), physical_item(), digital_item(), digital_item()],
    )


@pytest.mark.parametrize("format", ALL_FORMATS, ids=[f.id for f in ALL_FORMATS])
def test_a_re_digitised_work_of_any_format_takes_no_images_from_miro_alone(
    format: Format,
) -> None:
    sierra = re_digitised_sierra(format)

    assert image_data_rule.merge(sierra, [miro_work()]).data == []
    assert images_rule.merge(sierra, [miro_work()]).data == []


@pytest.mark.parametrize("format", ALL_FORMATS, ids=[f.id for f in ALL_FORMATS])
def test_a_re_digitised_work_emits_mets_images_only_for_pictures_and_ephemera(
    format: Format,
) -> None:
    sierra = re_digitised_sierra(format)
    mets = invisible_mets_work(num_images=1)
    sources = [miro_work(), mets, miro_work()]

    assert image_data_rule.merge(sierra, sources).data == []
    expected = mets.data.image_data if format.id in (Pictures.id, Ephemera.id) else []
    assert same_elements(images_rule.merge(sierra, sources).data, expected)
