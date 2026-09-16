from datetime import UTC, datetime

import polars as pl
from freezegun import freeze_time

from ingestor.models.indexable.concept import IndexableConcept
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.indexable.version import version_from_source_and_merged_time
from ingestor.models.indexable.work import IndexableWork
from ingestor.steps.ingestor_indexer import generate_operations
from tests.ingestor.test_images_transformer import _build_extracted_image


def _load_fixture_work() -> IndexableWork:
    df = pl.read_parquet("tests/fixtures/ingestor/works/00000000-00000010.parquet")
    return IndexableWork.from_raw_document(df.to_dicts()[0])


def test_generate_operations_works_version_from_source_and_merged_time() -> None:
    work = _load_fixture_work()

    ops = list(generate_operations("test-index", [work]))

    assert len(ops) == 1
    assert ops[0]["_index"] == "test-index"
    assert ops[0]["_id"] == work.get_id()
    assert ops[0]["_version_type"] == "external_gte"
    assert ops[0]["_version"] == version_from_source_and_merged_time(
        datetime.fromisoformat(work.debug.source.modified_time),
        datetime.fromisoformat(work.debug.merged_time),
    )


def test_generate_operations_works_version_uses_neither_timestamp_alone() -> None:
    """Ordering by merged time alone lets a stale source win, and by source time alone
    cannot order two merges of the same work. See wellcomecollection/platform#6686."""
    work = _load_fixture_work()

    version = list(generate_operations("test-index", [work]))[0]["_version"]

    source_millis = int(
        datetime.fromisoformat(work.debug.source.modified_time).timestamp() * 1000
    )
    merged_millis = int(
        datetime.fromisoformat(work.debug.merged_time).timestamp() * 1000
    )
    assert version not in (source_millis, merged_millis)


@freeze_time("2025-06-15T10:30:00Z")
def test_generate_operations_concepts_version_from_now() -> None:
    df = pl.read_parquet("tests/fixtures/ingestor/concepts/00000000-00000010.parquet")
    row = df.to_dicts()[0]
    concept = IndexableConcept.from_raw_document(row)

    ops = list(generate_operations("test-index", [concept]))

    assert len(ops) == 1
    assert ops[0]["_version_type"] == "external_gte"

    # Version should be epoch millis of frozen now()
    expected_version = int(
        datetime(2025, 6, 15, 10, 30, 0, tzinfo=UTC).timestamp() * 1000
    )
    assert ops[0]["_version"] == expected_version


@freeze_time("2025-04-21T12:00:00Z")
def test_generate_operations_images_version_from_modified_time() -> None:
    extracted = _build_extracted_image()
    image = IndexableImage.from_extracted_image(extracted)

    ops = list(generate_operations("test-index", [image]))

    assert len(ops) == 1
    assert ops[0]["_version_type"] == "external_gte"

    # modified_time from the fixture is "2025-04-01T10:00:00Z"
    expected_version = int(
        datetime(2025, 4, 1, 10, 0, 0, tzinfo=UTC).timestamp() * 1000
    )
    assert ops[0]["_version"] == expected_version
