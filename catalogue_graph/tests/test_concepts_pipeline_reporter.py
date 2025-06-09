import polars as pl
import pytest
from _pytest.monkeypatch import MonkeyPatch
from test_graph_remover import CATALOGUE_CONCEPTS_REMOVED_IDS_URI
from test_mocks import MockSmartOpen, fixed_datetime
from test_utils import load_fixture

from concepts_pipeline_reporter import (
    ReporterConfig,
    get_indexer_report,
    get_remover_report,
)
from graph_remover import IDS_LOG_SCHEMA
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


def mock_deleted_ids_log_file() -> None:
    mock_data = {
        "timestamp": ["2024-01-02", "2024-01-02", "2023-01-02", "2023-04-07"],
        "id": ["u6jve2vb", "amzfbrbz", "q5a7uqkz", "s8f6cxcf"],
    }
    df = pl.DataFrame(mock_data, schema=IDS_LOG_SCHEMA)
    MockSmartOpen.mock_s3_parquet_file(CATALOGUE_CONCEPTS_REMOVED_IDS_URI, df)


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

    report = get_indexer_report(reporter_event, 1000, reporter_config)

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    expected_section_lines = [
        "- Index *concepts-indexed-2024-01-02* in pipeline-2024-01-01",
        "- Pipeline started *on Tuesday, January 2 at 12:00 PM *",
        "- It contains *1000* documents _(the same as the graph)_.",
        "- Pipeline took *2160 minutes* to complete.",
        "- The last update was on Tuesday, January 2 at 12:00 PM when 930 documents were indexed.",
    ]
    actual_section_text = report[0]["text"]["text"]
    for line in expected_section_lines:
        assert line in actual_section_text

    MockSmartOpen.reset_mocks()


def test_get_remover_report_success(
    monkeypatch: MonkeyPatch,
    reporter_event: ReporterEvent,
    reporter_config: ReporterConfig,
) -> None:
    monkeypatch.setattr(
        "concepts_pipeline_reporter.datetime", fixed_datetime(2024, 1, 2)
    )

    MockSmartOpen.mock_s3_file(
        f"{s3_url}/report.index_remover.json",
        load_fixture("reporter/report.index_remover.json"),
    )
    MockSmartOpen.open(f"{s3_url}/report.index_remover.json", "r")

    mock_deleted_ids_log_file()

    report = get_remover_report(
        reporter_event, reporter_config, ["catalogue_concepts"], ["nodes"]
    )

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    assert report[1]["type"] == "section"
    assert "catalogue_concepts__nodes" in report[0]["text"]["text"]
    assert "2" in report[1]["text"]["text"]  # deleted_count from the graph
    assert (
        "*2* documents were deleted" in report[1]["text"]["text"]
    )  # deleted_count from the index

    MockSmartOpen.reset_mocks()


def test_get_remover_report_failure(
    monkeypatch: MonkeyPatch,
    reporter_event: ReporterEvent,
    reporter_config: ReporterConfig,
) -> None:
    monkeypatch.setattr(
        "concepts_pipeline_reporter.datetime", fixed_datetime(2025, 1, 2)
    )

    mock_deleted_ids_log_file()

    report = get_remover_report(
        reporter_event, reporter_config, ["catalogue_concepts"], ["nodes"]
    )

    assert isinstance(report, list)
    assert report[0]["type"] == "section"
    assert report[1]["type"] == "section"
    assert "No nodes or edges were deleted from the graph" in report[0]["text"]["text"]
    assert (
        "Could not produce Concepts Index Remover report" in report[1]["text"]["text"]
    )

    MockSmartOpen.reset_mocks()
