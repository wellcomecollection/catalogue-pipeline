import json

import pytest
from test_mocks import MockCloudwatchClient, MockSmartOpen

from ingestor_loader import IngestorLoaderLambdaEvent
from ingestor_trigger_monitor import (
    IngestorTriggerMonitorConfig,
    IngestorTriggerMonitorLambdaEvent,
    handler,
)


def test_ingestor_trigger_monitor_success_no_previous() -> None:
    latest_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.trigger.json"
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.trigger.json"

    event = IngestorTriggerMonitorLambdaEvent(
        pipeline_date="2025-01-01",
        force_pass=False,
        report_results=True,
        events=[
            IngestorLoaderLambdaEvent(
                pipeline_date="2025-01-01",
                job_id="123",
                start_offset=0,
                end_index=1,
            )
        ],
    )

    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [
        {
            "namespace": "catalogue_graph_ingestor",
            "value": 1,
            "metric_name": "end_index",
            "dimensions": {
                "pipeline_date": "2025-01-01",
                "job_id": "123",
                "step": "ingestor_trigger_monitor",
            },
        }
    ]

    # assert reports are written in s3
    with MockSmartOpen.open(current_job_s3_url, "r") as f:
        assert json.load(f) == {"end_index": 1, "job_id": "123"}

    with MockSmartOpen.open(latest_s3_url, "r") as f:
        assert json.load(f) == {"end_index": 1, "job_id": "123"}


def test_ingestor_trigger_monitor_success_with_previous() -> None:
    latest_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.trigger.json"
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.trigger.json"

    MockSmartOpen.mock_s3_file(
        latest_s3_url,
        json.dumps(
            {
                "end_index": 100,
                # Test this get overwritten
                "job_id": "XXX",
            }
        ),
    )

    event = IngestorTriggerMonitorLambdaEvent(
        pipeline_date="2025-01-01",
        force_pass=False,
        report_results=True,
        events=[
            IngestorLoaderLambdaEvent(
                pipeline_date="2025-01-01",
                job_id="123",
                start_offset=0,
                end_index=110,
            )
        ],
    )
    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1, is_local=True)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [
        {
            "namespace": "catalogue_graph_ingestor",
            "value": 110,
            "metric_name": "end_index",
            "dimensions": {
                "pipeline_date": "2025-01-01",
                "job_id": "123",
                "step": "ingestor_trigger_monitor",
            },
        }
    ]

    # assert reports are written in s3
    with MockSmartOpen.open(current_job_s3_url, "r") as f:
        assert json.load(f) == {"end_index": 110, "job_id": "123"}

    with MockSmartOpen.open(latest_s3_url, "r") as f:
        assert json.load(f) == {"end_index": 110, "job_id": "123"}


def test_ingestor_trigger_monitor_failure_with_previous() -> None:
    latest_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/report.trigger.json"
    current_job_s3_url = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/123/report.trigger.json"

    latest_content = {"end_index": 100, "job_id": "XXX"}

    MockSmartOpen.mock_s3_file(latest_s3_url, json.dumps(latest_content))

    event = IngestorTriggerMonitorLambdaEvent(
        pipeline_date="2025-01-01",
        force_pass=False,
        report_results=True,
        events=[
            IngestorLoaderLambdaEvent(
                pipeline_date="2025-01-01",
                job_id="123",
                start_offset=0,
                end_index=111,
            )
        ],
    )

    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1, is_local=True)

    # assert this raises a ValueError
    with pytest.raises(ValueError):
        handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == []

    # assert current report is not written in s3
    assert current_job_s3_url not in MockSmartOpen.file_lookup
