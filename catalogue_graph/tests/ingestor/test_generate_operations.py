from datetime import UTC, datetime

import polars as pl
from freezegun import freeze_time

from ingestor.models.indexable.concept import IndexableConcept
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.indexable.work import IndexableWork
from ingestor.steps.ingestor_indexer import generate_operations
from tests.ingestor.test_images_transformer import _build_extracted_image


def test_generate_operations_works_version_from_source_modified_time() -> None:
    df = pl.read_parquet("tests/fixtures/ingestor/works/00000000-00000010.parquet")
    row = df.to_dicts()[0]
    work = IndexableWork.from_raw_document(row)

    ops = list(generate_operations("test-index", [work]))

    assert len(ops) == 1
    assert ops[0]["_index"] == "test-index"
    assert ops[0]["_id"] == work.get_id()
    assert ops[0]["_version_type"] == "external_gte"

    # Version should be epoch millis of source modified_time
    expected_dt = datetime.fromisoformat(work.debug.source.modified_time)
    expected_version = int(expected_dt.timestamp() * 1000)
    assert ops[0]["_version"] == expected_version


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
