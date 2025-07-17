import json

from test_mocks import MockSmartOpen

from ingestor_indexer_monitor import IngestorIndexerMonitorLambdaEvent, handler

MOCK_LATEST_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/report.indexer.json"
MOCK_CURRENT_JOB_S3_URI = "s3://wellcomecollection-catalogue-graph/ingestor/2025-01-01/2025-03-01/123/report.indexer.json"

pipeline_date = "2025-01-01"
index_date = "2025-03-01"


def get_mock_expected_report(success_count: int, previous_job_id: str | None) -> dict:
    return {
        "pipeline_date": pipeline_date,
        "index_date": index_date,
        "job_id": "123",
        "previous_job_id": previous_job_id,
        "success_count": success_count,
    }


def verify_s3_reports(success_count: int, previous_job_id: str | None) -> None:
    expected_report = get_mock_expected_report(success_count, previous_job_id)

    with MockSmartOpen.open(MOCK_CURRENT_JOB_S3_URI, "r") as f:
        assert json.load(f) == expected_report

    with MockSmartOpen.open(MOCK_LATEST_S3_URI, "r") as f:
        assert json.load(f) == expected_report


def test_ingestor_loader_monitor_success_no_previous() -> None:
    events = [
        IngestorIndexerMonitorLambdaEvent(
            pipeline_date=pipeline_date,
            index_date=index_date,
            job_id="123",
            success_count=23,
        ),
        IngestorIndexerMonitorLambdaEvent(
            pipeline_date=pipeline_date,
            index_date=index_date,
            job_id="123",
            success_count=27,
        ),
    ]

    handler(events)

    # assert reports are written in s3
    verify_s3_reports(50, None)


def test_ingestor_loader_monitor_success_with_previous() -> None:
    MockSmartOpen.mock_s3_file(
        MOCK_LATEST_S3_URI,
        json.dumps(
            {
                "pipeline_date": pipeline_date,
                "job_id": "122",
                "index_date": index_date,
                "previous_job_id": "121",
                "success_count": 62,
            }
        ),
    )

    events = [
        IngestorIndexerMonitorLambdaEvent(
            pipeline_date=pipeline_date,
            index_date=index_date,
            job_id="123",
            success_count=250,
        ),
        IngestorIndexerMonitorLambdaEvent(
            pipeline_date=pipeline_date,
            index_date=index_date,
            job_id="123",
            success_count=250,
        ),
    ]

    handler(events)

    # assert reports are written in s3
    verify_s3_reports(500, "122")
