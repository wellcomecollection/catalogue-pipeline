import json

import pytest
from test_mocks import MockCloudwatchClient, MockSmartOpen

from ingestor.steps.ingestor_indexer import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
)
from ingestor.steps.ingestor_loader_monitor import (
    IngestorLoaderMonitorConfig,
    IngestorLoaderMonitorLambdaEvent,
    handler,
)

MOCK_LATEST_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/report.loader.json"
MOCK_CURRENT_JOB_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/123/report.loader.json"


def get_mock_expected_report(record_count: int, file_size: int) -> dict:
    return {
        "pipeline_date": "2025-01-01",
        "index_date": "2025-03-01",
        "job_id": "123",
        "record_count": record_count,
        "total_file_size": file_size,
    }


def get_mock_expected_metric(file_size: int) -> dict:
    return {
        "namespace": "catalogue_graph_ingestor",
        "value": file_size,
        "metric_name": "total_file_size",
        "dimensions": {
            "pipeline_date": "2025-01-01",
            "index_date": "2025-03-01",
            "job_id": "123",
            "step": "ingestor_loader_monitor",
        },
    }


def get_mock_ingestor_indexer_object(
    file_name: str, content_length: int | None, record_count: int | None
) -> IngestorIndexerObject:
    return IngestorIndexerObject(
        s3_uri=f"s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/123/{file_name}.parquet",
        content_length=content_length,
        record_count=record_count,
    )


def verify_s3_reports(record_count: int, file_size: int) -> None:
    expected_report = get_mock_expected_report(record_count, file_size)

    with MockSmartOpen.open(MOCK_CURRENT_JOB_S3_URI, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(MOCK_LATEST_S3_URI, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_success_no_previous() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file", 1000, 100),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file", 2000, 200),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(3000)]

    # assert reports are written in s3
    verify_s3_reports(300, 3000)


def test_ingestor_loader_monitor_success_with_previous() -> None:
    MockSmartOpen.mock_s3_file(
        MOCK_LATEST_S3_URI,
        json.dumps(
            {
                "pipeline_date": "XXX",
                "index_date": "XXX",
                "job_id": "XXX",
                "record_count": 300,
                "total_file_size": 3000,
            }
        ),
    )

    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file", 1100, 110),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file", 2100, 210),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(3200)]

    # assert reports are written in s3
    verify_s3_reports(320, 3200)


def test_ingestor_loader_monitor_failure_with_previous() -> None:
    MockSmartOpen.mock_s3_file(
        MOCK_LATEST_S3_URI,
        json.dumps(
            {
                "pipeline_date": "XXX",
                "index_date": "XXX",
                "job_id": "XXX",
                "record_count": 300,
                "total_file_size": 3000,
            }
        ),
    )

    # This event has a much different total file size (2000 vs previous 3000)
    # which exceeds the 10% threshold
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file1", 800, 800),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file2", 1200, 120),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # assert this raises a ValueError due to percentage change exceeding threshold
    with pytest.raises(ValueError, match="Fractional change .* exceeds threshold"):
        handler(event, config)

    # assert no metrics are reported
    assert MockCloudwatchClient.metrics_reported == []

    # assert current report is not written in s3
    assert MOCK_CURRENT_JOB_S3_URI not in MockSmartOpen.file_lookup


def test_ingestor_loader_monitor_force_pass() -> None:
    MockSmartOpen.mock_s3_file(
        MOCK_LATEST_S3_URI,
        json.dumps(
            {
                "pipeline_date": "XXX",
                "index_date": "XXX",
                "job_id": "XXX",
                "record_count": 300,
                "total_file_size": 3000,
            }
        ),
    )

    # This event has a much different total file size (2000 vs previous 3000)
    # but will pass because force_pass is True
    event = IngestorLoaderMonitorLambdaEvent(
        force_pass=True,  # Force pass is enabled
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file1", 800, 80),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file2", 1200, 120),
            ),
        ],
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # This should not raise an exception due to force_pass=True
    handler(event, config)

    # Metrics should still be reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(2000)]

    # assert reports are written in s3
    verify_s3_reports(200, 2000)


def test_ingestor_loader_monitor_pipeline_date_mismatch() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file1", 1000, 100),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-02",  # Different pipeline date
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file2", 2000, 200),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # Assert this raises an AssertionError due to pipeline date mismatch
    with pytest.raises(AssertionError, match="pipeline_date mismatch"):
        handler(event, config)


def test_ingestor_loader_monitor_job_id_mismatch() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file1", 1000, 100),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="456",  # Different job ID
                object_to_index=get_mock_ingestor_indexer_object("file2", 2000, 200),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # Assert this raises an AssertionError due to job ID mismatch
    with pytest.raises(AssertionError, match="job_id mismatch"):
        handler(event, config)


def test_ingestor_loader_monitor_empty_content_length() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                # Empty content length
                object_to_index=get_mock_ingestor_indexer_object("file1", None, 100),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file2", 2000, 200),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # Assert this raises an AssertionError due to empty content length
    with pytest.raises(AssertionError, match="Empty content length"):
        handler(event, config)


def test_ingestor_loader_monitor_empty_record_count() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                # Empty record count
                object_to_index=get_mock_ingestor_indexer_object("file1", 1000, None),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-03-01",
                job_id="123",
                object_to_index=get_mock_ingestor_indexer_object("file2", 2000, 200),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # Assert this raises an AssertionError due to empty record count
    with pytest.raises(AssertionError, match="Empty record count"):
        handler(event, config)
