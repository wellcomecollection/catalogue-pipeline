import json

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
    IngestorStepEvent,
)
from ingestor.steps.ingestor_loader_monitor import (
    handler,
)

from tests.mocks import MockCloudwatchClient, MockSmartOpen

MOCK_CURRENT_JOB_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor_concepts/2025-01-01/2025-03-01/123/report.loader.json"


MOCK_STEP_EVENT = IngestorStepEvent(
    ingestor_type="concepts",
    pipeline_date="2025-01-01",
    index_date="2025-03-01",
    job_id="123",
)


def get_mock_expected_report(record_count: int, file_size: int) -> dict:
    return {
        **MOCK_STEP_EVENT.model_dump(),
        "record_count": record_count,
        "total_file_size": file_size,
    }


def get_mock_expected_metric(file_size: int) -> dict:
    return {
        "namespace": "catalogue_graph_pipeline",
        "value": file_size,
        "metric_name": "total_file_size",
        "dimensions": {
            "ingestor_type": "concepts",
            "pipeline_date": "2025-01-01",
            "index_date": "2025-03-01"
        },
    }


def get_mock_ingestor_indexer_object(
    file_name: str, content_length: int, record_count: int
) -> IngestorIndexerObject:
    return IngestorIndexerObject(
        s3_uri=f"s3://wellcomecollection-catalogue-graph/ingestor_concepts/2025-01-01/2025-03-01/123/{file_name}.parquet",
        content_length=content_length,
        record_count=record_count,
    )


def verify_s3_reports(record_count: int, file_size: int) -> None:
    expected_report = get_mock_expected_report(record_count, file_size)

    with MockSmartOpen.open(MOCK_CURRENT_JOB_S3_URI, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_success_no_previous() -> None:
    event = IngestorIndexerLambdaEvent(
        **MOCK_STEP_EVENT.model_dump(),
        objects_to_index=[
            get_mock_ingestor_indexer_object("file", 1000, 100),
            get_mock_ingestor_indexer_object("file", 2000, 200),
        ],
    )

    handler(event)

    # assert metrics are reported
    assert MockCloudwatchClient.metrics_reported == [get_mock_expected_metric(3000)]

    # assert reports are written in s3
    verify_s3_reports(300, 3000)
