"""The flat image rule collects the images of every source."""

from merger.rules.images import FlatImageMergeRule
from merger.rules.predicates import any_work
from tests.merger.works import miro_work, sierra_digital_work


def test_images_from_every_source() -> None:
    rule = FlatImageMergeRule(any_work, any_work)
    sources = [miro_work() for _ in range(5)]

    images = rule(sierra_digital_work(), sources)

    assert images is not None
    assert len(images) == 5
