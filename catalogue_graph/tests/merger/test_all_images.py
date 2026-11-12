"""The all-images rule collects the images of every claimed source."""

from merger.rules.images import AllImages
from merger.rules.predicates import any_work
from tests.merger.works import miro_work, sierra_digital_work, tei_work


class AnyImages(AllImages):
    accepts_lms_work = any_work
    accepts_source = any_work


def test_images_from_every_source() -> None:
    sources = [miro_work() for _ in range(5)]

    merged = AnyImages.apply(sierra_digital_work(), sources)

    assert merged is not None
    assert len(merged.value) == 5


def test_nothing_without_an_lms_work_in_the_group() -> None:
    assert AnyImages.apply(tei_work(), [miro_work()]) is None
