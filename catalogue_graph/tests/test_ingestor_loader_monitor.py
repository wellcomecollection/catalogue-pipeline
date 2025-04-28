import json

import pytest
from ingestor_indexer import IngestorIndexerLambdaEvent, IngestorIndexerObject
from ingestor_loader_monitor import (
    IngestorLoaderMonitorConfig,
    IngestorLoaderMonitorLambdaEvent,
    handler,
)
from test_mocks import MockCloudwatchClient, MockSmartOpen


def test_ingestor_loader_monitor_success_no_previous() -> None:
    latest_s3_url = (
        "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.loader.json"
    )
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.loader.json"

    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file.parquet",
                    content_length=1000,
                    record_count=100,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file.parquet",
                    content_length=2000,
                    record_count=200,
                ),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [
        {
            "namespace": "catalogue_graph_ingestor",
            "value": 3000,
            "metric_name": "total_file_size",
            "dimensions": {
                "pipeline_date": "2025-01-01",
                "job_id": "123",
                "step": "ingestor_loader_monitor",
            },
        }
    ]

    expected_report = {
        "pipeline_date": "2025-01-01",
        "job_id": "123",
        "record_count": 300,
        "total_file_size": 3000,
    }

    # assert reports are written in s3
    with MockSmartOpen.open(current_job_s3_url, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(latest_s3_url, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_success_with_previous() -> None:
    latest_s3_url = (
        "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.loader.json"
    )
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.loader.json"

    MockSmartOpen.mock_s3_file(
        latest_s3_url,
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file.parquet",
                    content_length=1100,
                    record_count=110,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file.parquet",
                    content_length=2100,
                    record_count=210,
                ),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [
        {
            "namespace": "catalogue_graph_ingestor",
            "value": 3200,
            "metric_name": "total_file_size",
            "dimensions": {
                "pipeline_date": "2025-01-01",
                "job_id": "123",
                "step": "ingestor_loader_monitor",
            },
        }
    ]

    expected_report = {
        "pipeline_date": "2025-01-01",
        "job_id": "123",
        "record_count": 320,
        "total_file_size": 3200,
    }

    # assert reports are written in s3
    with MockSmartOpen.open(current_job_s3_url, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(latest_s3_url, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_failure_with_previous() -> None:
    latest_s3_url = (
        "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.loader.json"
    )
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.loader.json"

    MockSmartOpen.mock_s3_file(
        latest_s3_url,
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=800,
                    record_count=80,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file2.parquet",
                    content_length=1200,
                    record_count=120,
                ),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # assert this raises a ValueError due to percentage change exceeding threshold
    with pytest.raises(ValueError, match="Percentage change .* exceeds threshold"):
        handler(event, config)

    # assert no metrics are reported
    assert MockCloudwatchClient.metrics_reported == []

    # assert current report is not written in s3
    assert current_job_s3_url not in MockSmartOpen.file_lookup


def test_ingestor_loader_monitor_force_pass() -> None:
    latest_s3_url = (
        "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.loader.json"
    )
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.loader.json"

    MockSmartOpen.mock_s3_file(
        latest_s3_url,
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=800,
                    record_count=80,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file2.parquet",
                    content_length=1200,
                    record_count=120,
                ),
            ),
        ],
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # This should not raise an exception due to force_pass=True
    handler(event, config)

    expected_report = {
        "pipeline_date": "2025-01-01",
        "job_id": "123",
        "record_count": 200,
        "total_file_size": 2000,
    }

    # Metrics should still be reported
    assert MockCloudwatchClient.metrics_reported == [
        {
            "namespace": "catalogue_graph_ingestor",
            "value": 2000,
            "metric_name": "total_file_size",
            "dimensions": {
                "pipeline_date": "2025-01-01",
                "job_id": "123",
                "step": "ingestor_loader_monitor",
            },
        }
    ]

    # Reports should be written to S3
    with MockSmartOpen.open(current_job_s3_url, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(latest_s3_url, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_pipeline_date_mismatch() -> None:
    event = IngestorLoaderMonitorLambdaEvent(
        events=[
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=1000,
                    record_count=100,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-02",  # Different pipeline date
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-02/123/file2.parquet",
                    content_length=2000,
                    record_count=200,
                ),
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=1000,
                    record_count=100,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="456",  # Different job ID
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/456/file2.parquet",
                    content_length=2000,
                    record_count=200,
                ),
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=None,  # Empty content length
                    record_count=100,
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file2.parquet",
                    content_length=2000,
                    record_count=200,
                ),
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
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file1.parquet",
                    content_length=1000,
                    record_count=None,  # Empty record count
                ),
            ),
            IngestorIndexerLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                job_id="123",
                object_to_index=IngestorIndexerObject(
                    s3_uri="s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/file2.parquet",
                    content_length=2000,
                    record_count=200,
                ),
            ),
        ]
    )

    config = IngestorLoaderMonitorConfig(percentage_threshold=0.1, is_local=True)

    # Assert this raises an AssertionError due to empty record count
    with pytest.raises(AssertionError, match="Empty record count"):
        handler(event, config)
