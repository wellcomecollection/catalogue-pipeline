import json

import pytest
from test_mocks import (
    MockCloudwatchClient,
    MockSmartOpen,
    get_mock_ingestor_loader_event,
)

from ingestor.models.step_events import IngestorTriggerMonitorLambdaEvent
from ingestor.steps.ingestor_trigger_monitor import (
    IngestorTriggerMonitorConfig,
    handler,
)

MOCK_LATEST_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/report.trigger.json"
MOCK_CURRENT_JOB_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/123/report.trigger.json"


def get_mock_expected_report(record_count: int) -> dict:
    return {
        "record_count": record_count,
        "job_id": "123",
        "pipeline_date": "2025-01-01",
        "index_date": "2025-03-01",
    }


def get_mock_expected_metric(record_count: int) -> dict:
    return {
        "namespace": "catalogue_graph_ingestor",
        "value": record_count,
        "metric_name": "record_count",
        "dimensions": {
            "pipeline_date": "2025-01-01",
            "index_date": "2025-03-01",
            "job_id": "123",
            "step": "ingestor_trigger_monitor",
        },
    }


def verify_s3_reports(record_count: int) -> None:
    expected_report = get_mock_expected_report(record_count)

    with MockSmartOpen.open(MOCK_CURRENT_JOB_S3_URI, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(MOCK_LATEST_S3_URI, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_trigger_monitor_success_no_previous() -> None:
    event = IngestorTriggerMonitorLambdaEvent(
        ingestor_type="concepts",
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        job_id="123",
        force_pass=False,
        report_results=True,
        events=[
            get_mock_ingestor_loader_event(
                "123",
                0,
                1,
            )
        ],
    )

    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(1)]

    # assert reports are written in s3
    verify_s3_reports(1)


def test_ingestor_trigger_monitor_success_with_previous() -> None:
    MockSmartOpen.mock_s3_file(
        MOCK_LATEST_S3_URI,
        json.dumps(
            {
                "record_count": 100,
                # Test this gets overwritten
                "job_id": "XXX",
                "pipeline_date": "XXX",
                "index_date": "XXX",
            }
        ),
    )

    event = IngestorTriggerMonitorLambdaEvent(
        ingestor_type="concepts",
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        job_id="123",
        force_pass=False,
        report_results=True,
        events=[get_mock_ingestor_loader_event("123", 0, 110)],
    )
    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1)

    handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(110)]

    # assert reports are written in s3
    verify_s3_reports(110)


def test_ingestor_trigger_monitor_failure_with_previous() -> None:
    latest_content = {"record_count": 100, "job_id": "XXX"}

    MockSmartOpen.mock_s3_file(MOCK_LATEST_S3_URI, json.dumps(latest_content))

    event = IngestorTriggerMonitorLambdaEvent(
        ingestor_type="concepts",
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        job_id="123",
        force_pass=False,
        report_results=True,
        events=[get_mock_ingestor_loader_event("123", 0, 111)],
    )

    config = IngestorTriggerMonitorConfig(percentage_threshold=0.1)

    # assert this raises a ValueError
    with pytest.raises(ValueError):
        handler(event, config)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == []

    # assert current report is not written in s3
    assert MOCK_CURRENT_JOB_S3_URI not in MockSmartOpen.file_lookup
