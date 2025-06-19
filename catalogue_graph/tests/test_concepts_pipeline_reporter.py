import pytest
from _pytest.monkeypatch import MonkeyPatch
from test_mocks import MockSmartOpen, fixed_datetime
from test_utils import load_fixture

from concepts_pipeline_reporter import (
    ReporterConfig,
    get_indexer_report,
)
from models.step_events import ReporterEvent

pipeline_date = "2024-01-01"
index_date = "2024-01-02"
job_id = "20240102T1200"
s3_bucket = "test-bucket"
s3_prefix = "test-prefix"
s3_url = f"s3://{s3_bucket}/{s3_prefix}/{pipeline_date}/{index_date}"


@pytest.fixture
def reporter_event() -> ReporterEvent:
    return ReporterEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        success_count=1000,
    )


@pytest.fixture
def reporter_config() -> ReporterConfig:
    return ReporterConfig(
        ingestor_s3_bucket=s3_bucket,
        ingestor_s3_prefix=s3_prefix,
        slack_secret="test-secret",
        is_local=True,
    )


def test_get_indexer_report_failure(
    reporter_event: ReporterEvent, reporter_config: ReporterConfig
) -> None:
    report = get_indexer_report(reporter_event, 123, reporter_config)

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    assert "Could not produce Concepts Indexer report" in report[0]["text"]["text"]


def test_get_indexer_report_success(
    monkeypatch: MonkeyPatch,
    reporter_event: ReporterEvent,
    reporter_config: ReporterConfig,
) -> None:
    monkeypatch.setattr(
        "concepts_pipeline_reporter.datetime", fixed_datetime(2024, 1, 4)
    )
    
    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{job_id}/report.indexer.json",
        load_fixture("reporter/report.indexer.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{job_id}/report.indexer.json", "r")

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/{job_id}/report.index_remover.json",
        load_fixture("reporter/report.index_remover.json"),
    )
    MockSmartOpen.open(f"{s3_url}/{job_id}/report.index_remover.json", "r")
    
    report = get_indexer_report(reporter_event, 1000, reporter_config)

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    expected_section_lines = [
        "- Index *concepts-indexed-2024-01-02* in pipeline-2024-01-01",
        "- Pipeline started *on Tuesday, January 2 at 12:00 PM *",
        "- It contains *1000* documents _(the same as the graph)_.",
        "- Pipeline took *2160 minutes* to complete.",
        "- *2* documents were deleted from the graph.",
        "- The last update was on Tuesday, January 2 at 12:00 PM when 930 documents were indexed.",
    ]
    actual_section_text = report[0]["text"]["text"]
    for line in expected_section_lines:
        assert line in actual_section_text

    MockSmartOpen.reset_mocks()
