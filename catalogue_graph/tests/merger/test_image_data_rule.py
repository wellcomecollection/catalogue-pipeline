"""The images kept on a merged work: Miro images only."""

import pytest

from merger.rules.images import ImageDataRule
from tests.merger.works import (
    calm_work,
    digcode_identifier,
    invisible_mets_work,
    miro_work,
    same_elements,
    sierra_digital_work,
    tei_work,
)


def test_no_images_without_sources() -> None:
    assert ImageDataRule.merge(sierra_digital_work(), []).data == []


def test_images_from_miro() -> None:
    miro_works = [miro_work() for _ in range(5)]

    result = ImageDataRule.merge(sierra_digital_work(), miro_works)

    assert same_elements(
        result.data, [i for w in miro_works for i in w.data.image_data]
    )


def test_no_images_from_mets() -> None:
    miro_works = [miro_work() for _ in range(5)]
    mets = invisible_mets_work(num_images=3)

    result = ImageDataRule.merge(sierra_digital_work(), [*miro_works, mets])

    assert len(result.data) == 5
    assert same_elements(
        [i.locations for i in result.data],
        [w.data.image_data[0].locations for w in miro_works],
    )


@pytest.mark.parametrize("digcode", ["digmiro", "digaids"])
def test_miro_images_are_discarded_for_a_re_digitised_sierra_work(digcode: str) -> None:
    sierra = sierra_digital_work(other_identifiers=[digcode_identifier(digcode)])
    miro_works = [miro_work() for _ in range(5)]

    assert ImageDataRule.merge(sierra, miro_works).data == []


def test_re_digitised_miro_images_are_discarded_for_a_tei_target() -> None:
    sierra = sierra_digital_work(other_identifiers=[digcode_identifier("digaids")])
    miro_works = [miro_work() for _ in range(5)]
    mets = invisible_mets_work(num_images=3)

    result = ImageDataRule.merge(tei_work(), [*miro_works, sierra, mets])

    assert result.data == []


def test_re_digitised_miro_images_are_discarded_for_a_calm_target() -> None:
    sierra = sierra_digital_work(other_identifiers=[digcode_identifier("digmiro")])
    miro_works = [miro_work() for _ in range(5)]

    assert ImageDataRule.merge(calm_work(), [*miro_works, sierra]).data == []


def test_miro_images_are_kept_for_a_sierra_work_with_other_digcodes() -> None:
    sierra = sierra_digital_work(other_identifiers=[digcode_identifier("digicon")])
    miro_works = [miro_work() for _ in range(5)]

    result = ImageDataRule.merge(sierra, miro_works)

    assert same_elements(
        result.data, [i for w in miro_works for i in w.data.image_data]
    )


def test_miro_images_are_discarded_when_a_re_digitised_digcode_is_among_others() -> (
    None
):
    sierra = sierra_digital_work(
        other_identifiers=[
            digcode_identifier("digicon"),
            digcode_identifier("digmiro"),
            digcode_identifier("digpicture"),
        ]
    )
    miro_works = [miro_work() for _ in range(5)]

    assert ImageDataRule.merge(sierra, miro_works).data == []
