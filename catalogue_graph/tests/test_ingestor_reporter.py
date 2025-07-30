import pytest
from _pytest.monkeypatch import MonkeyPatch
from test_mocks import MockSmartOpen, fixed_datetime
from test_utils import load_fixture

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor.steps.ingestor_reporter import (
    ReporterConfig,
    get_ingestor_report,
)
from models.step_events import IngestorStepEvent

pipeline_date = "2024-01-01"
index_date = "2024-01-02"
job_id = "20240102T1200"
previous_job_id = "20240101T1200"

s3_url = f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{pipeline_date}/{index_date}"


@pytest.fixture
def reporter_event() -> IngestorStepEvent:
    return IngestorStepEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
    )


@pytest.fixture
def reporter_config() -> ReporterConfig:
    return ReporterConfig(
        slack_secret="test-secret",
        is_local=True,
    )


def test_get_ingestor_report_failure(
    reporter_event: IngestorStepEvent, reporter_config: ReporterConfig
) -> None:
    report = get_ingestor_report(reporter_event, reporter_config)

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    assert "Could not produce Concepts Indexer report" in report[0]["text"]["text"]


def test_get_ingestor_report_success(
    monkeypatch: MonkeyPatch,
    reporter_event: IngestorStepEvent,
    reporter_config: ReporterConfig,
) -> None:
    monkeypatch.setattr(
        "ingestor.steps.ingestor_reporter.datetime", fixed_datetime(2024, 1, 6)
    )

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{job_id}/report.trigger.json",
        load_fixture("reporter/report.trigger.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{job_id}/report.trigger.json", "r")

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{job_id}/report.indexer.json",
        load_fixture("reporter/report.indexer.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{job_id}/report.indexer.json", "r")

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{previous_job_id}/report.indexer.json",
        load_fixture("reporter/report.indexer_previous.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{previous_job_id}/report.indexer.json", "r")

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{job_id}/report.deletions.json",
        load_fixture("reporter/report.deletions.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{job_id}/report.deletions.json", "r")

    report = get_ingestor_report(reporter_event, reporter_config)

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    expected_section_lines = [
        "- Index *concepts-indexed-2024-01-02* in pipeline-2024-01-01",
        "- Pipeline started *on Tuesday, January 2 at 12:00 PM *",
        "- It contains *980* documents _(the same as the graph)_.",
        "- Pipeline took *5040 minutes* to complete.",
        "- *2* documents were deleted from the index.",
        "- The last update was on Monday, January 1 at 12:00 PM when 980 documents were indexed.",
    ]

    actual_section_text = report[0]["text"]["text"]
    print("Actual section text:", actual_section_text)
    for line in expected_section_lines:
        assert line in actual_section_text

    MockSmartOpen.reset_mocks()
