"""The merger step: what it reads, writes and reports on each run."""

import polars as pl
import pytest

from merger import components as storage
from merger.models.step_events import MergerEvent
from merger.steps import merger
from tests.merger.support import (
    components,
    iceberg_table,
    labelled,
    s3_read_error,
    works,
)
from tests.mocks import MockSmartOpen
from utils.aws import df_from_s3_parquet, df_to_s3_parquet

EVENT = MergerEvent(pipeline_date="2026-01-01", graph_date="2026-01-02", job_id="job-1")
GRAPH = {"A": "B", "B": "", "C": "D", "D": "", "E": ""}


def read_like_s3(uri: str) -> pl.DataFrame:
    """The mock raises KeyError for a missing file; S3 reports a missing key."""
    if uri not in MockSmartOpen.file_lookup:
        raise s3_read_error("NoSuchKey")
    return df_from_s3_parquet(uri)


def run(
    monkeypatch: pytest.MonkeyPatch, event: MergerEvent = EVENT, snapshot_id: int = 7
) -> merger.MergerResult:
    table = iceberg_table(works(GRAPH), snapshot_id)
    monkeypatch.setattr(merger, "get_works_identified_table", lambda use_rest: table)
    monkeypatch.setattr(storage, "df_from_s3_parquet", read_like_s3)
    return merger.handler(event)


def written(uri: str) -> dict[str, str]:
    return labelled(df_from_s3_parquet(uri))


def test_first_run_reports_every_work(monkeypatch: pytest.MonkeyPatch) -> None:
    result = run(monkeypatch)

    assert (result.work_count, result.changed_work_count) == (5, 5)
    assert result.changed_component_count == 3
    assert written(result.changed_components_s3_uri) == {"A": "AB", "C": "CD", "E": "E"}


def test_later_run_reports_only_changed_works(monkeypatch: pytest.MonkeyPatch) -> None:
    MockSmartOpen.mock_s3_parquet_file(
        EVENT.components_s3_uri, components({"A": "AB", "C": "C", "D": "D", "E": "E"})
    )

    result = run(monkeypatch)

    assert (result.changed_work_count, result.changed_component_count) == (2, 1)
    assert written(result.changed_components_s3_uri) == {"C": "CD"}


def test_every_run_replaces_the_baseline_with_the_whole_graph(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    MockSmartOpen.mock_s3_parquet_file(EVENT.components_s3_uri, components({"Z": "Z"}))

    run(monkeypatch)

    assert written(EVENT.components_s3_uri) == {"A": "AB", "C": "CD", "E": "E"}


def test_changed_works_are_written_before_the_baseline(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def refuse_baseline(frame: pl.DataFrame, uri: str) -> None:
        if uri == EVENT.components_s3_uri:
            raise OSError("S3 is down")
        df_to_s3_parquet(frame, uri)

    monkeypatch.setattr(merger, "df_to_s3_parquet", refuse_baseline)

    with pytest.raises(OSError):
        run(monkeypatch)

    assert written(EVENT.get_s3_uri("changed_components")) == {
        "A": "AB",
        "C": "CD",
        "E": "E",
    }
    assert EVENT.components_s3_uri not in MockSmartOpen.file_lookup


def test_reads_the_current_snapshot_unless_one_is_given(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    assert run(monkeypatch, snapshot_id=7).iceberg_snapshot_id == 7

    pinned = EVENT.model_copy(update={"iceberg_snapshot_id": 5})
    assert run(monkeypatch, pinned, snapshot_id=7).iceberg_snapshot_id == 5


def test_an_empty_table_is_an_error(monkeypatch: pytest.MonkeyPatch) -> None:
    table = iceberg_table(works({}))
    table.current_snapshot.return_value = None
    monkeypatch.setattr(merger, "get_works_identified_table", lambda use_rest: table)

    with pytest.raises(ValueError, match="empty"):
        merger.handler(EVENT)


def test_the_baseline_is_shared_by_jobs_and_the_changed_file_is_not() -> None:
    other_job = EVENT.model_copy(update={"job_id": "job-2"})

    assert EVENT.components_s3_uri == other_job.components_s3_uri
    assert EVENT.components_s3_uri.endswith(
        "/graph-2026-01-02/pipeline-2026-01-01/merger/components.parquet"
    )
    assert EVENT.get_s3_uri("changed_components").endswith(
        "/graph-2026-01-02/pipeline-2026-01-01/merger/full/job-job-1/changed_components.parquet"
    )
    assert EVENT.get_s3_uri("x") != other_job.get_s3_uri("x")
