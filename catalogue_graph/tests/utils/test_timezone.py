"""
Regression tests for timestamps which must not depend on the local timezone.
"""

import time
from collections.abc import Iterator
from datetime import UTC, datetime

import pytest
from freezegun import freeze_time
from pymarc.record import Field, Record

from adapters.transformers.builders.ebsco_work_builder import EbscoWorkBuilder
from adapters.transformers.marc.parsers.date_from_005 import datetime_from_005
from ingestor.extractors.images.images_extractor import ExtractedImage
from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.augmented.image import AugmentedImage
from ingestor.models.debug.work import VisibleWorkDebug
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.merged.work import VisibleMergedWork
from ingestor.models.neptune.query_result import WorkHierarchy
from tests.test_utils import load_json_fixture
from utils.timezone import convert_datetime_to_utc_iso

FROZEN_TIME = "2001-01-01T01:01:01Z"


def _extracted_work() -> VisibleExtractedWork:
    work = VisibleMergedWork(**load_json_fixture("ingestor/single_merged.json"))
    hierarchy = WorkHierarchy(id=work.state.canonical_id, ancestors=[], children=[])
    return VisibleExtractedWork(work=work, hierarchy=hierarchy, concepts=[])


@pytest.fixture(autouse=True)
def local_timezone_ahead_of_utc(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    """Run every test in this module one hour ahead of UTC, with no DST."""
    # POSIX inverts the sign of these zone names, so `Etc/GMT-1` is UTC+1.
    monkeypatch.setenv("TZ", "Etc/GMT-1")
    time.tzset()
    yield
    monkeypatch.undo()
    time.tzset()


@freeze_time(FROZEN_TIME)
def test_work_indexed_time_is_the_current_utc_time() -> None:
    debug = VisibleWorkDebug.from_merged_work(_extracted_work().work)

    assert debug.indexed_time == FROZEN_TIME


@freeze_time(FROZEN_TIME)
def test_image_indexed_time_is_the_current_utc_time() -> None:
    image = AugmentedImage.model_validate(
        load_json_fixture("ingestor/single_augmented_image.json")
    )

    indexable = IndexableImage.from_extracted_image(
        ExtractedImage(image=image, work=_extracted_work())
    )

    assert indexable.debug.indexed_time == FROZEN_TIME


@freeze_time(FROZEN_TIME)
def test_source_work_modified_time_is_the_current_utc_time() -> None:
    record = Record(fields=[Field(tag="001", data="ebs1234")])

    builder = EbscoWorkBuilder(record, last_modified=datetime(2020, 1, 1, tzinfo=UTC))

    assert builder.deleted_work_state.modified_time == FROZEN_TIME


def test_datetime_from_005_is_read_as_utc() -> None:
    parsed = datetime_from_005("20251225123045.0")

    assert parsed == datetime(2025, 12, 25, 12, 30, 45, tzinfo=UTC)
    assert convert_datetime_to_utc_iso(parsed) == "2025-12-25T12:30:45Z"
