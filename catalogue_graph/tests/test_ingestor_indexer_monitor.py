import json

from test_mocks import MockSmartOpen

from ingestor.models.step_events import IngestorIndexerMonitorLambdaEvent
from ingestor.steps.ingestor_indexer_monitor import handler

MOCK_CURRENT_JOB_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor_concepts/2025-01-01/2025-03-01/123/report.indexer.json"

pipeline_date = "2025-01-01"
index_date = "2025-03-01"


def get_mock_expected_report(success_count: int) -> dict:
    return {
        "pipeline_date": pipeline_date,
        "index_date": index_date,
        "ingestor_type": "concepts",
        "job_id": "123",
        "load_format": "parquet",
        "window": None,
        "es_pit_id": None,
        "success_count": success_count,
    }


def verify_s3_reports(success_count: int) -> None:
    expected_report = get_mock_expected_report(success_count)

    with MockSmartOpen.open(MOCK_CURRENT_JOB_S3_URI, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_indexer_monitor_success() -> None:
    event = IngestorIndexerMonitorLambdaEvent(
        ingestor_type="concepts",
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id="123",
        success_count=500,
    )
    handler(event)

    # assert reports are written in s3
    verify_s3_reports(500)
